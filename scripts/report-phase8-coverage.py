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


def read_scope_counters(path):
    """Partition package counters for reporting only; never exclude from bundle gates.

    Package counters avoid double counting lines shared by nested classes.
    Unknown packages stay in business scope rather than silently disappearing.
    """
    scopes = {name: {"packages": [], "counters": {
        kind: {"covered": 0, "missed": 0} for kind in ("LINE", "BRANCH")}}
        for name in ("business", "model_contracts", "framework_wiring", "migrations")}
    packages = ET.parse(path).getroot().findall("package")
    if not packages:
        raise ValueError(f"Missing package coverage evidence: {path}")
    for package in packages:
        name = package.get("name", "")
        segments = set(name.split("/"))
        if name == "db/migration" or name.startswith("db/migration/"):
            scope = "migrations"
        elif segments & {"dto", "entity", "enums", "exception", "contracts"}:
            scope = "model_contracts"
        elif segments & {"config", "configuration", "security"}:
            scope = "framework_wiring"
        else:
            scope = "business"
        scopes[scope]["packages"].append(name)
        for counter in package.findall("counter"):
            kind = counter.get("type")
            if kind not in scopes[scope]["counters"]:
                continue
            for key in ("covered", "missed"):
                value = int(counter.attrib[key])
                if value < 0:
                    raise ValueError(f"Negative package counter: {path}")
                scopes[scope]["counters"][kind][key] += value
    bundle = read_counters(path)
    for kind in bundle:
        for key in bundle[kind]:
            if sum(scope["counters"][kind][key] for scope in scopes.values()) != bundle[kind][key]:
                raise ValueError(f"Package partition does not match bundle {kind} {key}: {path}")
    for scope in scopes.values():
        scope["packages"].sort()
        scope["percent"] = {key: percentage(value) for key, value in scope["counters"].items()}
    return scopes


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
            else:
                row["reporting_scopes"] = read_scope_counters(path)
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
                              "Top-level percentages are bundle-wide; service reporting scopes partition "
                              "all packages without exclusions or new thresholds. Null means no branch opportunities.",
                      "modules": rows, "errors": errors}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
