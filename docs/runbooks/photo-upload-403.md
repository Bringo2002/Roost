# Photo upload fails with HTTP 403

## Symptom

Adding photos in the app's list-a-property flow shows a snackbar such as:

> A photo failed to upload: Exception: R2 rejected upload (HTTP 403)

The progress card shows "Uploading N of M..." but the photo count never goes up.

The word "R2" is stale wording from when storage was Cloudflare R2. The 403 comes
from whichever S3-compatible store the presigned URL points at (now Amazon S3).

## How uploads work

1. The app asks the backend for an upload URL (`POST /api/properties/presign-upload`).
2. The backend signs a short-lived **presigned PUT URL** using the credentials in
   `/etc/roost/env` (`S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_BUCKET`,
   `S3_REGION`).
3. The app sends the file straight to S3 with `PUT`, with the same
   `Content-Type` the server signed (`image/jpeg` for photos).
4. S3 checks the signature **and** the signing identity's permissions.

Consequence: a presigned URL is only as powerful as the IAM user that signed it.
The backend can happily hand out URLs that S3 will refuse, so a bad key or a
missing permission shows up as a 403 on the phone, not as an error in the
backend log.

## Triage, in order

Run these **on the server**. See
[EC2 access and env changes](ec2-access-and-env-changes.md) for how to get there
and the paste rules.

**1. Load the stored values (prints no secrets)**

```
set -a; source <(sudo grep -E '^S3_(ACCESS_KEY_ID|SECRET_ACCESS_KEY|BUCKET|REGION)=' /etc/roost/env); set +a
echo "bucket=$S3_BUCKET region=$S3_REGION"
```

Both must be non-empty and match the real bucket and its region.

**2. Does the stored key pair authenticate at all?** (needs no permissions)

```
AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$S3_SECRET_ACCESS_KEY AWS_DEFAULT_REGION=$S3_REGION aws sts get-caller-identity
```

Expected: JSON whose `Arn` ends in the app's IAM user.

**3. Can that user write to the bucket?**

```
echo ok > /tmp/t.txt
AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$S3_SECRET_ACCESS_KEY AWS_DEFAULT_REGION=$S3_REGION aws s3api put-object --bucket "$S3_BUCKET" --key properties/_perm-test.txt --body /tmp/t.txt
```

Expected: an `ETag`. Clean up with the same prefix and `aws s3api delete-object`.

**4. See S3's own error code** (the app hides the response body). Replace the
placeholders; the route names come from `PropertyController` and
`AuthController`, so check them there if you get a 404.

```
BASE=<api-base-url>
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"<landlord-email>","password":"<password>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
URL=$(curl -s -X POST "$BASE/api/properties/presign-upload" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"photo"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["uploadUrl"])')
printf 'x' > /tmp/t.jpg
curl -s -i -X PUT "$URL" -H 'Content-Type: image/jpeg' --data-binary @/tmp/t.jpg | head -30
```

The XML body contains `<Code>` and `<Message>`. This sequence was written but not
run during the incident below; the server-side steps 1 to 3 found the cause first.

## What the result means

| Result | Meaning | Fix |
|---|---|---|
| Step 2: `SignatureDoesNotMatch` | The stored secret does not belong to the stored key ID, even if its length is right | Re-enter the secret from the source (see below) |
| Step 2: `InvalidClientTokenId` | Key ID is wrong, inactive, or deleted | Check the key in IAM; create a new one |
| Step 3: `AccessDenied`, "no identity-based policy allows `s3:PutObject`" | The IAM policy does not cover `bucket/*` | Fix the policy (below) |
| Step 3: `AccessDenied` mentioning an explicit deny | A bucket policy, permissions boundary or SCP blocks it | Look for a `Deny` statement |
| `301`, or `AuthorizationHeaderMalformed` | `S3_REGION` is not the bucket's region | Correct `S3_REGION` |
| `RequestTimeTooSkewed`, `Request has expired` | Server clock is off, or the presigned URL is older than its validity window | `timedatectl`; retry the upload |
| `NoSuchBucket` (404) | Wrong bucket name | Correct `S3_BUCKET` |
| All of 1 to 3 pass but the phone still gets 403 | Problem is in the presigned URL itself (signed headers, checksum parameters) | Run step 4 and inspect `X-Amz-SignedHeaders` and the query parameter names |

An IAM clue: on the user's **Security credentials** tab, an access key whose
"Last used" says **Never used** after upload attempts means S3 never accepted a
request signed with it. The server is using a different or mangled key.

## The incident (2026-10-31), after moving hosting to AWS

Two independent faults were stacked, and the first hid the second.

1. **Wrong secret on the server.** `S3_ACCESS_KEY_ID` matched the IAM console and
   `S3_SECRET_ACCESS_KEY` had the right length (40), but
   `aws sts get-caller-identity` returned `SignatureDoesNotMatch`. The stored
   secret was a different 40 characters from the real one, most likely a
   transcription error (`I`, `l`, `1`, `O` and `0` look alike). Re-entering the
   secret by pasting it from the downloaded file fixed it.
2. **IAM policy did not cover the objects.** With a valid key, step 3 returned
   `AccessDenied: no identity-based policy allows the s3:PutObject action`. The
   attached policy showed `PutObject` as an allowed action, but its object
   resource did not match `<bucket>/properties/...`. Replacing it with the policy
   below fixed uploads.

Time was also lost to running server commands on a laptop, an SSH security-group
rule for the wrong IP, multi-line pastes into `read`, and empty variables in a new
SSH session. All are covered in the EC2 runbook.

## The IAM policy

Attach to the app's IAM user. Object actions need the `/*` resource; `ListBucket`
needs the bare bucket ARN. Add one entry per bucket the app uses.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ObjectAccess",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::<bucket>/*"]
    },
    {
      "Sid": "ListBucket",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": ["arn:aws:s3:::<bucket>"]
    }
  ]
}
```

The bucket's default encryption was SSE-S3 (`AES256`), so no KMS permissions are
needed. If a bucket uses SSE-KMS, the user also needs access to that key.

## After uploads work

- **Check that photos display.** Open an uploaded photo's URL in a browser.
  `S3_PUBLIC_BASE_URL` in the env template points at the bucket's own S3 address,
  which only works if the objects are publicly readable (bucket policy for
  `properties/*`) or if you put a CDN in front and point the variable there.
- **Rotate any key whose secret was exposed**, following the secret hygiene steps
  in the EC2 runbook.

## Ideas to make this faster next time

Not implemented yet:

- Check the S3 credentials at startup (for example a `HeadBucket` call) so a wrong
  key fails the deploy instead of failing uploads.
- Have the app log S3's `<Code>` for a failed upload, and reword the "R2" message.
- Give the instance an IAM role instead of long-lived keys in the env file. The
  backend currently builds its S3 credentials only from the env keys, so this
  needs a code change.
