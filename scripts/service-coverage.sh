#!/usr/bin/env bash
# Aggregate JaCoCo line/branch coverage per consolidated service: merges every
# module's target/jacoco.exec (boot tests included) over the main classes of all
# non-boot modules. Run after `mvn verify` for the services of interest.
# Usage: bash scripts/service-coverage.sh match order delivery ...
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
M2="${HOME}/.m2/repository"
CP="${M2}/org/jacoco/org.jacoco.core/0.8.13/org.jacoco.core-0.8.13.jar:${M2}/org/ow2/asm/asm/9.8/asm-9.8.jar:${M2}/org/ow2/asm/asm-commons/9.8/asm-commons-9.8.jar:${M2}/org/ow2/asm/asm-tree/9.8/asm-tree-9.8.jar"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" -cp "$CP" "${ROOT_DIR}/scripts/coverage/ServiceCoverage.java"
java -cp "${CP}:${OUT}" ServiceCoverage "$ROOT_DIR" "$@"
