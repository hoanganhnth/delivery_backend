# Shipper Authentication Contract

The app uses one Gateway origin from `src/config/runtime.ts`.

- Password login uses `POST /api/auth/login`.
- Google login uses `POST /api/auth/social-login` with role `SHIPPER`.
- Access and refresh tokens are stored through the session port; device identity
  is persisted rather than hard-coded.
- Protected 401 responses use single-flight refresh. Login and social-login 401
  responses keep their original failure and do not attempt a pre-session refresh.
- Logout attempts refresh-token revocation, then always clears local session and
  leaves the authenticated UI even if the remote revoke fails.

Self-registration, forgot/change password, settlement and payout remain hidden
until the backend exposes a recoverable, authorised contract for them.
