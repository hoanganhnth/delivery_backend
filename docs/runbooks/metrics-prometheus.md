# Metrics and Prometheus Runbook

## Access and safety

Each service exposes `/actuator/prometheus` only on management port `9090`.
Compose does not publish this port, Prometheus, or Grafana to the host. Access
them through the internal platform network or an authenticated operational
proxy; do not add a public port mapping. Set `METRICS_PROMETHEUS_ENABLED=false`
to disable the exporter for an environment.

All custom `delivery_*` metrics use only the bounded `application`, `event`,
and `outcome` labels. Do not add user, order, delivery, email, address, token,
topic payload, or exception-message labels.

## What to inspect

- Slow requests: `http_server_requests_seconds` exposes count, sum, buckets,
  and configured p50/p95/p99 percentiles; group by `application`, `uri`, and
  `status`. A 5xx rate is the count filtered with `status=~"5.."`.
- Kafka: use `kafka_consumer_fetch_manager_records_lag_max` for consumer lag,
  `spring_kafka_listener_seconds` for listener timing, and
  `delivery_kafka_events_total{event="retry|dlt"}` for recovery failures.
- Database and Redis: Boot binders export `hikaricp_connections_*`,
  `jdbc_connections_*`/`jdbc_query_*` where supported, and `lettuce_command_*`.
- COD: `delivery_business_events_total{event="delivery_completed"}` should
  converge with `event="settlement_completed"`; a widening gap plus Kafka DLT
  or listener errors requires pausing reconciliation and investigating the
  durable settlement receipt before replaying events.

## Initial triage

1. Confirm Prometheus target health, then inspect error rate and p95/p99.
2. If Kafka lag rises, inspect listener time and retry/DLT counts before
   increasing consumers; verify downstream PostgreSQL/Redis pool health.
3. For COD divergence, locate the failing `delivery.completed` consumer and
   follow the settlement idempotency/replay procedure. Never replay based on a
   metric alone.
