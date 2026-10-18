# US18 account management

POST /api/auth/login {email,password} returns a one-hour bearer accessToken.
Admin APIs: POST /api/users, PUT /api/users/{id}, GET /api/users/{id}, POST /api/users/{id}/lock and /unlock. Updates preserve user_id and never delete meetings or attendance. Email uniqueness and role/department references are checked. Self-lock and disabling/demoting the last active Admin are rejected.
Admin/PM/Member role names are supported; existing Vietnamese role names remain compatible. No role IDs are rewritten or reseeded automatically.
All protected RBAC requests reread ACTIVE status from database; mobile JWT conversion does the same. A previously issued token stops working while the account is locked. Unlock permits that token again until expiry.
Configure app.mobile.jwt.secret as a Base64 secret with at least 32 random bytes for deployment. If absent, a random startup key is used (tokens expire on restart). Issuer defaults to meeting-management and audience to meeting-client.
ERD user has no credential column. This implementation retains the pre-existing password_hash extension documented in US02_shared_erd_extensions.sql; it does not add another account table or alter meeting/user keys. Apply that existing extension explicitly when provisioning the original ERD; no database changes are executed by this task.
