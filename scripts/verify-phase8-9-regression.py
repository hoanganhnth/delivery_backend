#!/usr/bin/env python3
"""Static regression gates for Phase 8/9 high-change domains.

This intentionally reports only production Java sources. Test cleanup and
migration assertions may use DELETE/deleteAll without being business paths.
"""
from pathlib import Path
import importlib.util
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DOMAINS = ("search-service", "analytics-service", "promotion-service",
           "flashsale-service", "livestream-service", "simulator-service")
HARD_DELETE = re.compile(r"\.(?:delete|deleteById|deleteAll)\s*\(")
REQUIRED = (
    "docs/contracts/soft-delete-and-tombstone-v1.md",
    "docs/contracts/events/entity-tombstone-v1.json",
    "docs/runbooks/search-projection-replay.md",
    "docs/runbooks/simulator-scenarios-phase8-9.md",
    "livestream-service/src/main/java/db/migration/V3__livestream_product_soft_delete.java",
)

def main() -> int:
    errors = []
    spec = importlib.util.spec_from_file_location(
        "module_boundaries", ROOT / "scripts/verify-module-boundaries.py"
    )
    boundaries = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(boundaries)
    errors.extend(boundaries.verify(ROOT))
    for relative in REQUIRED:
        if not (ROOT / relative).exists():
            errors.append(f"missing required artifact: {relative}")
    for domain in DOMAINS:
        source_root = ROOT / domain / "src/main/java"
        if not source_root.exists():
            continue
        for source in source_root.rglob("*.java"):
            text = source.read_text(encoding="utf-8")
            for number, line in enumerate(text.splitlines(), 1):
                if HARD_DELETE.search(line):
                    errors.append(f"hard delete in production path: {source.relative_to(ROOT)}:{number}")
    if errors:
        print("Phase 8/9 regression audit FAILED")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("Phase 8/9 regression audit PASSED")
    print(f"Checked domains: {', '.join(DOMAINS)}")
    print("Checked module boundaries, declared service dependencies, hard-delete calls and required artifacts.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
