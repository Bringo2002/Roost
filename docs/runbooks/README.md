# Runbooks

Short, tested procedures for problems that have already happened once.

| Runbook | Use it when |
|---|---|
| [EC2 access and env changes](ec2-access-and-env-changes.md) | You need to get onto the server or change a value in `/etc/roost/env` |
| [Photo upload fails with HTTP 403](photo-upload-403.md) | The app shows "R2 rejected upload (HTTP 403)" when adding photos |
| [Login fails with "incorrect ending byte at 44"](login-invalid-base64-jwt-secret.md) | Email and Google login both fail with a base64 decoding message |

## Writing and publishing a runbook

This repository is public, so a runbook must contain no live identifiers.

- Use placeholders: `<bucket>`, `<instance-public-dns>`, `<key>.pem`,
  `<api-base-url>`.
- Leave out AWS account IDs, access key IDs, IAM ARNs, instance and security
  group IDs, IP addresses, hostnames, bucket names and CDN domains.
- Never include a secret or a downloaded credentials file, even an old one.
- Say what was verified and what was not. Mark commands that were written but
  not run.

Before committing, search the folder for leaks:

```
grep -rnE 'AKIA[0-9A-Z]{16}|[0-9]{12}|ec2-[0-9-]+|cloudfront\.net|\.pem\b' docs/runbooks
```

Review every hit. Placeholders such as `<key>.pem` are fine; real values are not.
