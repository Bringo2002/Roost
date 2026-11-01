# EC2 access and changing server configuration

How to get onto the backend server and change `/etc/roost/env` without breaking
it. The other runbooks in this folder rely on these procedures.

## Where things live

| What | Where |
|---|---|
| Server | One EC2 instance (Amazon Linux 2023), login user `ec2-user` |
| App process | systemd unit `roost` (`sudo systemctl status roost`) |
| Spring profile | `aws`, set in the unit file. `Procfile` and `Dockerfile` still say `production`; they are leftovers from the previous host and are not used on EC2 |
| Configuration | `/etc/roost/env`, loaded by systemd as an `EnvironmentFile` |
| Logs | `/var/log/roost/app.log`, or `sudo journalctl -u roost -n 100` |

The env file is read **only when the service starts**. Editing it changes
nothing until you run `sudo systemctl restart roost`.

## Getting onto the server

**SSH from your machine**

```
chmod 400 ~/.ssh/<key>.pem
ssh -i ~/.ssh/<key>.pem ec2-user@<instance-public-dns>
```

Keep the `.pem` file outside any git repository. A key file inside a repo can be
committed by `git add -A`.

**Browser alternative:** EC2 console, select the instance, **Connect**, **EC2
Instance Connect**. No key file or security-group rule is needed for your IP.

**You are on the server when the prompt looks like** `[ec2-user@ip-172-31-...]$`.
If you see your laptop's prompt instead, the commands below will fail with
`/etc/roost/env: No such file or directory` or `Unit roost.service not found`.
That is harmless, but nothing was changed.

### SSH times out

A timeout (not "permission denied") means the packets never arrived. The usual
cause is the security group's SSH rule, which only allows one address.

1. On the machine you are SSHing from, run `curl -s https://checkip.amazonaws.com`.
   Use this address, not the one the AWS console's "My IP" shows: a VPN that only
   covers your browser makes them differ.
2. EC2 console, the instance's **Security** tab, the security group, **Edit
   inbound rules**. Set the SSH (port 22) source to that address with `/32`.
3. Never open port 22 to `0.0.0.0/0`. Ports 80 and 443 are public by design.
4. If it still fails, check the instance's public DNS name. It changes after a
   stop/start unless an Elastic IP is attached. Some mobile and public networks
   also block outbound port 22.

Your address can change between sessions, so a timeout that returns later usually
means the rule needs updating again.

## Changing a value in `/etc/roost/env`

Follow these rules. Each one comes from a mistake that cost time.

1. **One command per paste.** Pasting several lines into a terminal while a
   `read` prompt is waiting feeds the following lines to `read` as its answers,
   and nothing runs as intended.
2. **Read secrets with `read`, never type them into the command line**, so they do
   not land in shell history:
   ```
   read -p "Key ID: " KID
   read -s -p "Secret: " SEC; echo
   ```
3. **Strip whitespace and check the length** before using the values:
   ```
   KID=$(printf %s "$KID" | tr -d '[:space:]'); SEC=$(printf %s "$SEC" | tr -d '[:space:]'); echo "lens: ${#KID} ${#SEC}"
   ```
   An AWS access key ID is 20 characters and a secret access key is 40.
4. **Test the value before writing it.** For AWS keys:
   ```
   AWS_ACCESS_KEY_ID=$KID AWS_SECRET_ACCESS_KEY=$SEC AWS_DEFAULT_REGION=<region> aws sts get-caller-identity
   ```
   It must print the expected IAM user. If it prints `SignatureDoesNotMatch`,
   do not write the value.
5. **Write it, then clear the secret from the shell:**
   ```
   sudo sed -i "s|^S3_ACCESS_KEY_ID=.*|S3_ACCESS_KEY_ID=${KID}|; s|^S3_SECRET_ACCESS_KEY=.*|S3_SECRET_ACCESS_KEY=${SEC}|" /etc/roost/env
   unset SEC
   ```
   The `|` delimiter is safe for AWS secrets, which only contain letters, digits,
   `/` and `+`. For other values, check that they contain no `|` or `&`.
6. **Restart and verify:**
   ```
   sudo systemctl restart roost; sleep 8; sudo systemctl is-active roost
   ```
   It must print `active`.

### Looking at what the server holds without printing secrets

```
sudo grep '^S3_ACCESS_KEY_ID=' /etc/roost/env | cat -A
sudo awk '/^S3_SECRET_ACCESS_KEY=/{sub(/^S3_SECRET_ACCESS_KEY=/,""); print "secret length:", length($0)}' /etc/roost/env
```

Strip the `NAME=` prefix with `sub` instead of splitting on `=` with `awk -F=`:
values that themselves contain `=` (base64 padding, for example) would be cut
short and report the wrong length.

`cat -A` shows invisible characters: a trailing space, or `^M` (a Windows line
ending) before the final `$`.

A correct length does **not** prove a correct value. See the
[photo upload 403 runbook](photo-upload-403.md) for a case where a 40-character
secret was the wrong 40 characters.

### Shell variables do not survive a new SSH session

If you reconnect, variables like `$S3_REGION` are empty again. A symptom is an
AWS CLI error such as `Invalid endpoint: https://s3..amazonaws.com`. Reload them:

```
set -a; source <(sudo grep -E '^S3_(ACCESS_KEY_ID|SECRET_ACCESS_KEY|BUCKET|REGION)=' /etc/roost/env); set +a
echo "bucket=$S3_BUCKET region=$S3_REGION"
```

## Secret hygiene

- Never paste a secret into a chat, ticket, or screenshot. The IAM "download .csv"
  file contains the secret in plain text; delete it once the value is on the
  server.
- Treat any secret that was visible anywhere as compromised and rotate it:
  create the new credential, put it on the server using the steps above, confirm
  the app works, then deactivate and later delete the old one.
