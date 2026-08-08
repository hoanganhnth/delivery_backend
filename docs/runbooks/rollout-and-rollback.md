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

It is deliberately not a production deployment controller: Compose local builds
mutable images and cannot provide a safe canary. Production follows the main
runbook's immutable-image, readiness, metric/SLO and rollback requirements.
