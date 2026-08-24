# Rolling/Canary Rollout And Rollback

Use this procedure for every backend release. It preserves the Gateway-only
public boundary and uses Actuator readiness, not process liveness, as the traffic
gate.

1. Select immutable image digests, config label and secret versions. Run schema
   migrations with their documented backward-compatible order before deploying a
   service that requires them; never deploy code that needs a migration not yet
   applied.
2. Start a canary with startup timeout. It must load Config Server data, mount
   required secrets, register `UP` in Eureka and return readiness `UP`. Liveness
   only authorizes restart; it never authorizes traffic.
3. Route a small share of Gateway traffic only after readiness. Drain existing
   connections before terminating a replaced instance; retain Kafka consumers
   long enough to commit/rebalance cleanly. Run the Gateway COD smoke after the
   canary and after each completed rollout.
4. Promote only while error rate, p95 latency and Kafka consumer lag remain
   below the release SLO thresholds approved for the environment. Treat a
   readiness transition, dependency failure, elevated error rate/latency, or
   growing lag as a stop condition.
5. On stop condition, remove the canary from traffic, roll back to the prior
   image/config label/secret version, confirm readiness and run the COD smoke.
   If the incident is registry-specific, the private static-routes overlay is
   the emergency route rollback; it never publishes service ports.

Record image digest, config label, secret version identifiers (not values),
registry instance IDs, readiness timestamps, smoke output and rollback decision
in the deployment record. Rehearse dependency failure, readiness recovery,
service re-registration and rollback before production promotion.

## Checkout quote and idempotency rollout

`order-service` migrations V13/V14 add durable checkout quotes and scoped
create-order idempotency receipts. Roll them out before the code that depends
on them, then keep `ORDER_QUOTE_ENFORCEMENT_ENABLED=false` while compatible
Flutter clients are released.

1. Verify preview returns `quoteId` and ISO `expiresAt`; verify a normal create
   with the UUID `Idempotency-Key` returns one order.
2. Re-send the identical create request after a simulated client timeout and
   confirm it returns the same order ID with one `order.created` outbox event.
3. While the first request is still in flight, send the same request again and
   confirm the second request waits for or reports `409 IDEMPOTENCY_IN_PROGRESS`
   and never creates a second order. Retry that same key after completion and
   confirm it returns the original order.
4. Verify a changed current price returns `409 PRICE_CHANGED` with
   `error.details.quote`, and an expired quote returns `409 QUOTE_EXPIRED`.
5. Canary `ORDER_QUOTE_ENFORCEMENT_ENABLED=true` only after clients have
   demonstrated both fields on normal checkout traffic. Watch 400 missing-pair
   responses and typed 409 rates alongside normal create latency/error rates.

To roll back enforcement, set only
`ORDER_QUOTE_ENFORCEMENT_ENABLED=false` and redeploy configuration. Do not
delete quote/receipt rows or roll back V13/V14: they are retry evidence for
already accepted orders and are cleaned by their configured 24-hour retention.

## JWKS migration on local/staging Compose

The final JWKS Gateway release must never be started on top of access tokens
without `kid`. For a Compose staging rehearsal, use the phase runner rather
than replacing every container in one `up --build` command:

```bash
# If this machine has no existing pre-JWKS Gateway, create a separate legacy
# checkout and start it first. This refuses to overwrite an existing stack.
bash scripts/bootstrap-jwks-legacy-compose.sh

# The legacy Gateway must still run for Waves 1 and 2.
bash scripts/rollout-jwks-compose.sh wave1

# Login through that legacy Gateway. Store only the new access token in a
# protected, untracked file, then validate it without printing it.
JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
  bash scripts/rollout-jwks-compose.sh verify-token

# Only after the runner-recorded 15-minute access-token TTL plus five-minute
# skew buffer has elapsed:
JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
  bash scripts/rollout-jwks-compose.sh wave2
JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
  bash scripts/rollout-jwks-compose.sh wave3
```

The runner preserves the old Gateway through Waves 1 and 2, checks Auth
readiness and the public-only JWK shape, requires a post-Wave-1 RS256 token,
then verifies a resource-service request before the Gateway cutover. It records
only timestamps, release revision and public `kid` in ignored
`.jwks-rollout-state`; it never records JWTs or PEM contents.

Wave 2 first packages and builds the complete resource-service set, then
replaces and readiness-checks one service at a time. Its default readiness
window is ten minutes so a local Docker host cannot turn a concurrent JVM
startup burst into a false migration failure.

It is deliberately not a production deployment controller: Compose local builds
mutable images and cannot provide a safe canary. Production follows the main
runbook's immutable-image, readiness, metric/SLO and rollback requirements.
