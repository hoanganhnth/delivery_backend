# Firebase support chat deployment

The backend mints Firebase custom tokens after validating the platform JWT.
Clients do not use a Firebase API key as an authorization mechanism and do not
choose their own sender identity.

## Local enablement

1. Mount the Firebase service-account JSON outside the repository.
2. Set the same project credential path for `auth-service`:

```text
FIREBASE_CHAT_ENABLED=true
FIREBASE_SERVICE_ACCOUNT_KEY_PATH=file:/run/secrets/firebase.json
```

3. Deploy Rules and indexes from this directory after selecting the intended
   Firebase project:

```bash
firebase use delivery-233fb
firebase deploy --only firestore:rules,firestore:indexes
```

When running the backend with Docker Compose, add the optional Firebase overlay
alongside the existing secret overlay. It reads the service-account JSON from
an operator-owned path and never copies it into the repository:

```bash
export FIREBASE_SERVICE_ACCOUNT_FILE=/absolute/path/firebase-service-account.json
docker compose \
  -f backend_delivery/docker-compose.yml \
  -f backend_delivery/docker-compose.secrets.yml \
  -f backend_delivery/docker-compose.firebase-chat.yml \
  up --build auth-service api-gateway
```

Keep the overlay disabled until the Firestore Rules and index deployment has
been verified. Removing the overlay or setting `FIREBASE_CHAT_ENABLED=false`
is the rollback path.

The feature stays unavailable when the service account is missing or the
backend flag is false. Never commit the service-account JSON or a token.

## Data contract

New documents use the existing top-level collections:

```text
conversations/{conversationId}
messages/{messageId}
```

Every new conversation/message carries `principalId` and
`senderPrincipalId`/`senderRole`. Older documents without these canonical
identity fields are intentionally not treated as trusted data by the Rules;
backfill them through an explicit migration before production use.
