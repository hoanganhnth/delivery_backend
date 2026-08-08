# Local Observability Runtime Verification

## Purpose

This runbook verifies that local observability is functioning as a connected
system, rather than merely confirming that Prometheus and Grafana containers
exist.

## Command

From `backend_delivery/`, after the core Compose stack is healthy:

```bash
COMPOSE_PROJECT_NAME=backend_delivery \
  bash scripts/verify-observability-runtime.sh
```

The verifier requires no public Grafana/Prometheus host port. It checks from
inside the private Compose network that:

1. Prometheus reports `/-/ready`.
2. Grafana reports database health through `/api/health`.
3. Each of the eight configured core scrape targets is present and `up` in the
   Prometheus target API.
4. The provisioned Grafana dashboard has UID `delivery-operations`.

It reads the Grafana administrator password only inside the Grafana container
to query its own local API. It never prints the password or token material.

## Healthy result

```text
PASS: Prometheus ready, Grafana healthy/dashboard provisioned, and all core scrape targets are up.
```

If a target is missing or `down`, inspect the target service's private
`/actuator/prometheus` endpoint and its readiness before changing Prometheus
configuration. Do not expose management ports merely to debug a scrape.

## Scope limit

This verifies local metrics/dashboard wiring only. The OTLP collector currently
uses a debug exporter and there is no approved durable log/trace backend,
Alertmanager receiver, retention policy or on-call route yet. Those remain
production platform decisions.
