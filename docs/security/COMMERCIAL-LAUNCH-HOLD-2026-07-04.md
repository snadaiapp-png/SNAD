# Commercial Launch Hold

Commercial launch remains blocked until provider rotations are verified and the replacement credentials are confirmed in Render without entering source control.

## Owner-reported status

- Database password rotation: reported complete by project owner.
- Resend API key rotation: reported complete by project owner.

## Remaining provider configuration gate

Render must be updated with the replacement `DATABASE_PASSWORD` and `SECURITY_NOTIFICATION_RESEND_API_KEY` using Render secret management only. Secret values must not be committed, logged, passed as workflow-dispatch inputs, or posted in issues or pull requests.

The gate closes only after:

1. Render holds both replacement values.
2. Database-backed readiness returns `UP`.
3. The Resend credential validates successfully.
4. A sanitized evidence record confirms success without exposing values.
