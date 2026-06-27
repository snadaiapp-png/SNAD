# SNAD Account Recovery and Email Runbook

## 1. Scope

This runbook governs password-recovery notifications, administrator-issued set-password links, password-change confirmations, and operational testing of the approved SNAD system mailbox.

The approved mailbox identity is controlled through `SECURITY_NOTIFICATION_FROM` and is recorded in the formal execution tracking issue. Do not duplicate provider credentials or mailbox secrets in repository files.

## 2. Security rules

1. Never send a plaintext password.
2. Never send a temporary password selected by an administrator.
3. Send only an expiring, single-use recovery or set-password link.
4. Do not reveal whether an email address exists in the system.
5. Do not log the raw reset value.
6. Store only the reset-value hash.
7. Revoke the reset value when delivery fails.
8. Revoke existing refresh sessions after a successful password change.
9. Store provider credentials only in the deployment secret manager.
10. Do not paste provider passwords, application passwords, or bearer values into issues, pull requests, logs, or documentation.

## 3. Required backend configuration

```text
SECURITY_NOTIFICATION_PROVIDER=http
SECURITY_NOTIFICATION_ENDPOINT=<authorized HTTPS delivery endpoint>
SECURITY_NOTIFICATION_BEARER_TOKEN=<deployment-managed secret>
SECURITY_NOTIFICATION_FROM=<approved SNAD mailbox>
APPLICATION_BASE_URL=https://snad-app.vercel.app
```

The endpoint must use HTTPS. A disabled provider outside local/test must fail closed.

## 4. End-to-end activation procedure

### 4.1 Provider readiness

1. Select the approved email delivery service or internal notification endpoint.
2. Verify that it is authorized to send using the approved mailbox identity.
3. Store the endpoint and credential in Render secret environment variables.
4. Set the approved sender and application URL.
5. Deploy an exact reviewed `main` commit.
6. Confirm that no secret value appears in startup logs.

### 4.2 Recovery test

Use a dedicated pilot user whose mailbox is controlled by the project owner.

1. Open the SNAD login screen.
2. Select **نسيت كلمة المرور؟**.
3. Enter the pilot user's email.
4. Confirm the UI displays the generic success response.
5. Confirm one email is delivered.
6. Confirm the sender identity matches `SECURITY_NOTIFICATION_FROM`.
7. Confirm the email contains an HTTPS link to `/reset-password?token=...`.
8. Confirm the email does not contain a password.
9. Open the link and set a new password.
10. Confirm the link cannot be reused.
11. Confirm old sessions are rejected.
12. Confirm the password-change notification is delivered.

### 4.3 Administrator test

1. Sign in as an authorized administrator.
2. Open user management.
3. Select **إرسال رابط كلمة المرور** for an active pilot user.
4. Confirm the user receives a one-time set-password link.
5. Confirm the administrator never sees the raw password.
6. Confirm legacy `newPassword` and `forceChange` payloads are rejected.

## 5. Expected results

```text
MAILBOX_SEND_RECEIVE: PASS
BACKEND_PROVIDER_CONFIGURED: PASS
GENERIC_RECOVERY_RESPONSE: PASS
ONE_TIME_LINK_DELIVERED: PASS
PLAINTEXT_PASSWORD_PRESENT: NO
RAW_TOKEN_IN_LOGS: NO
LINK_SINGLE_USE: PASS
LINK_EXPIRY: PASS
OLD_SESSIONS_REVOKED: PASS
CHANGE_CONFIRMATION_DELIVERED: PASS
```

Do not mark the email integration operationally accepted unless all relevant results are evidenced.

## 6. Failure handling

### Delivery endpoint unavailable

- Keep the response generic.
- Revoke the generated recovery value.
- Record a sanitized operational error.
- Do not expose provider status to the end user.
- Do not retry indefinitely inside the HTTP request.

### Email not received

1. Check provider response and sanitized backend logs.
2. Check spam and filtering rules.
3. Check sender authorization.
4. Check destination accuracy.
5. Check provider quota and suppression lists.
6. Generate a new recovery request only after the cause is understood.

### Link expired or used

- Ask the user to request a new link.
- Do not reactivate or extend the old value.
- Do not expose whether the old value was previously valid.

### Suspected mailbox compromise

1. Disable the notification provider.
2. Rotate mailbox and provider credentials.
3. Revoke active recovery values.
4. Review delivery and authentication logs.
5. Record an incident.
6. Re-enable only after security approval.

## 7. Evidence record

For every formal test, record:

- Tested `main` SHA.
- Deployment identifier.
- Backend health status.
- Test timestamp in UTC.
- Test user identifier without unnecessary personal data.
- Provider message identifier.
- Delivery result.
- Single-use result.
- Expiry result.
- Session-revocation result.
- Confirmation-message result.
- Sanitized screenshots or logs.

Never store the raw recovery value in evidence.

## 8. Acceptance and governance

A basic mailbox send/receive test has been completed and recorded in the execution tracker. Backend end-to-end acceptance remains pending until runtime provider variables are configured and the complete procedure passes.

This runbook does not close Issue #101, pass OWASP Final, or authorize commercial production.
