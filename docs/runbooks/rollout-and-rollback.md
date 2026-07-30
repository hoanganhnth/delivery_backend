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
