#!/usr/bin/env python3
"""Report JaCoCo bundle coverage; enforce existing core gates, never invent missing data.

Run after a fresh Maven verify. Output is JSON; this command does not run tests.
"""
import argparse
import json
import subprocess
from decimal import Decimal
from pathlib import Path
import xml.etree.ElementTree as ET

SERVICES = ("search", "analytics", "promotion", "flashsale", "livestream", "simulator")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def read_counters(path):
    root = ET.parse(path).getroot()
    result = {kind: {"covered": 0, "missed": 0} for kind in ("LINE", "BRANCH")}
    for counter in root.findall("counter"):
        kind = counter.get("type")
        if kind in result:
            values = {key: int(counter.attrib[key]) for key in ("covered", "missed")}
            if min(values.values()) < 0:
                raise ValueError(f"Negative counter: {path}")
            result[kind] = values
    if sum(result["LINE"].values()) == 0:
        raise ValueError(f"Missing executable line counters: {path}")
    return result


def percentage(counter):
    total = sum(counter.values())
    return None if total == 0 else round(100 * counter["covered"] / total, 2)


def meets_gate(counter, threshold):
    total = sum(counter.values())
    return total == 0 or Decimal(counter["covered"]) >= Decimal(threshold) * total


def collect(root):
    errors, rows = [], []
    poms = sorted((root / "modules").glob("*/*/pom.xml"))
    poms = [p for p in poms if p.parent.name.endswith(("-domain", "-application"))]
    if not poms:
        errors.append("No extracted core modules found")
    targets = [(p, True) for p in poms]
    targets += [(root / f"{name}-service/pom.xml", False) for name in SERVICES]
    for pom, core in targets:
        name = str(pom.parent.relative_to(root))
        path = pom.parent / "target/site/jacoco/jacoco.xml"
        try:
            project = ET.parse(pom).getroot()
            counters = read_counters(path)
            row = {"module": name, "scope": "core" if core else "service",
                   "report": str(path.relative_to(root)), "counters": counters,
                   "percent": {key: percentage(value) for key, value in counters.items()}}
            if core:
                for kind in ("LINE", "BRANCH"):
                    threshold = project.findtext(
                        f"m:properties/m:delivery.coverage.{kind.lower()}.minimum", namespaces=NS)
                    if threshold is None or Decimal(threshold) < Decimal("0.85"):
                        errors.append(f"{name}: missing or weakened {kind} gate")
                    elif not meets_gate(counters[kind], threshold):
                        errors.append(f"{name}: {kind} below configured gate {threshold}")
            rows.append(row)
        except (OSError, ET.ParseError, ValueError, ArithmeticError) as error:
            errors.append(f"{name}: {error}")
    return rows, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    rows, errors = collect(root)
    revision = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"],
                              capture_output=True, text=True, check=True).stdout.strip()
    print(json.dumps({"inspected_commit": revision,
                      "note": "Coverage belongs to the last verify run; rerun after source changes. "
                              "Percentages are bundle-wide. Null means no branch opportunities.",
                      "modules": rows, "errors": errors}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
