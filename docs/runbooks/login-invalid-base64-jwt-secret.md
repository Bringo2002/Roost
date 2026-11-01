# Login fails with "Input byte array has incorrect ending byte at 44"

## Symptom

Email login and Google login both fail right after the credentials are accepted,
and the app shows the raw message:

> Input byte array has incorrect ending byte at 44

Both paths fail the same way because both end by issuing a JWT, and issuing a
JWT needs the signing secret.

## Cause

`JWT_SECRET` is a base64 string, and the backend decodes it with Java's strict
`java.util.Base64` decoder every time it signs or verifies a token. A 32-byte
secret is exactly 44 characters ending in `=`. If anything follows that value
(a trailing newline, a space, a stray quote, or a second copy pasted after it),
the decoder rejects it and reports the offset of the first unexpected character:
44.

Because the variable exists, the app **starts normally**. The failure only
appears on the first login.

### Why it can start after "it was working"

The incident this runbook comes from began after a framework migration that also
replaced the JWT library. The previous library's decoder ignored stray
whitespace, so a newline in the variable did no harm; the new code decodes
strictly. That ordering is the most likely explanation, but it was inferred from
the commit history and not proven. The practical lesson holds either way:
a value that works with one decoder can break with another.

## Diagnose

On the server (see [EC2 access and env changes](ec2-access-and-env-changes.md)):

```
sudo awk '/^JWT_SECRET=/{sub(/^JWT_SECRET=/,""); print "length:", length($0)}' /etc/roost/env
sudo grep '^JWT_SECRET=' /etc/roost/env | cat -A
```

- A healthy 32-byte secret prints `length: 44`. `45` or more means extra characters.
- In the `cat -A` output the line must end `=$`. Something like `= $`, `=^M$` or
  `="$` shows the culprit.

Do not use `awk -F=` for this value: base64 padding is `=`, so the field split
cuts the value short and prints 43.

On another host, print the byte count from the shell that holds the variable:
`printf %s "$JWT_SECRET" | wc -c` should print 44.

## Fix

Generate a new secret and write it without a trailing newline. `$(...)` strips
the newline that `openssl` prints.

```
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" /etc/roost/env
sudo systemctl restart roost; sleep 8; sudo systemctl is-active roost
```

Re-run the length check (it must print 44), then log in again.

Rotating the secret signs everyone out once. That is harmless before launch and
a short inconvenience afterwards.

## Hardening status

At the time of writing the backend still decodes the secret strictly and trusts
the value as given. A planned change trims surrounding whitespace, requires valid
base64 of at least 32 bytes, and fails the boot with a length-only message
(never the secret), so a bad value stops the deploy instead of breaking logins.
Update this section when that change is merged.
