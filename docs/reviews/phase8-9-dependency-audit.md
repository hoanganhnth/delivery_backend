# Phase 8–9 dependency and regression audit

The executable gate is `scripts/verify-phase8-9-regression.py`. It checks the
high-change services' production Java sources for direct repository hard-delete
calls and verifies the required tombstone, replay, simulator, and livestream
artifacts exist. Test fixtures and migration constraint assertions are excluded
from the hard-delete scan because they intentionally reset or validate data.

Run from the repository root:

```bash
python3 scripts/verify-phase8-9-regression.py
```

The audit is a static gate, not a replacement for the full Maven reactor,
Docker-backed integration tests, coverage report, or production dependency
review. Those remain required evidence before Phase 9 exit approval.
