# Simulator scenarios Phase 8–9

`Phase8ScenarioCatalog.defaults()` is the deterministic regression catalog. Each
scenario has a stable seed so a failed run can be reproduced and compared across
builds. The catalog is intentionally production-isolated; simulator traffic must
use the simulator namespace, actor pool, and non-production Kafka topics.

Scenarios cover duplicate Kafka delivery, consumer restart, search replay, voucher
contention, flash-sale stock contention, livestream checkout retry, and soft-delete
recovery. Record the run id, seed, correlation id, decision trace, and recovery
result in the simulator journal. Never point a simulator run at production URLs,
databases, caches, or Kafka topics.
