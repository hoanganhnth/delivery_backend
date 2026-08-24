#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly RUN_ID="$(date +%s)-$$"
readonly IMAGE_TAG="delivery-artifact-freshness-check:${RUN_ID}"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/delivery-artifact-check.XXXXXX")"
readonly WORK_DIR

if [[ -z "$WORK_DIR" || "$WORK_DIR" != */delivery-artifact-check.* ]]; then
  printf '%s\n' "Refusing unsafe temporary directory: $WORK_DIR" >&2
  exit 1
fi

cleanup() {
  docker image rm --force "$IMAGE_TAG" >/dev/null 2>&1 || true
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT INT TERM

mkdir -p "$WORK_DIR/fixture/src/main/java/example" "$WORK_DIR/fixture/target"
cp "$ROOT_DIR/Dockerfile" "$WORK_DIR/Dockerfile"

printf '%s\n' '<project/>' > "$WORK_DIR/pom.xml"
printf '%s\n' '<project/>' > "$WORK_DIR/fixture/pom.xml"
printf '%s\n' 'package example; final class Fixture {}' \
  > "$WORK_DIR/fixture/src/main/java/example/Fixture.java"
printf '%s\n' 'fixture artifact' > "$WORK_DIR/fixture/target/fixture.jar"

write_manifest() {
  {
    shasum -a 256 "$WORK_DIR/pom.xml" "$WORK_DIR/fixture/pom.xml"
    find "$WORK_DIR/fixture/src" -type f -print | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "$file"
    done
  } | awk '{print $1}' | shasum -a 256 | awk '{print $1}' \
    > "$WORK_DIR/fixture/target/.docker-artifact-input.sha256"
}

# Establish deterministic ordering independent of filesystem timestamp precision.
touch -t 202601010100 "$WORK_DIR/pom.xml" \
  "$WORK_DIR/fixture/pom.xml" \
  "$WORK_DIR/fixture/src/main/java/example/Fixture.java"
touch -t 202601010101 "$WORK_DIR/fixture/target/fixture.jar"
write_manifest

docker build --quiet --tag "$IMAGE_TAG" --build-arg SERVICE_PATH=fixture \
  --file "$WORK_DIR/Dockerfile" "$WORK_DIR" >/dev/null

printf '%s\n' '// source changed after package' \
  >> "$WORK_DIR/fixture/src/main/java/example/Fixture.java"
touch -t 202601010102 "$WORK_DIR/fixture/src/main/java/example/Fixture.java"

if docker build --tag "$IMAGE_TAG" --build-arg SERVICE_PATH=fixture \
    --file "$WORK_DIR/Dockerfile" "$WORK_DIR" \
    >"$WORK_DIR/stale-build.log" 2>&1; then
  printf '%s\n' "Docker accepted a stale packaged JAR." >&2
  exit 1
fi

if ! grep -Fq 'is stale (input checksum changed)' "$WORK_DIR/stale-build.log" \
    || ! grep -Fq 'run scripts/package-compose-services.sh first' "$WORK_DIR/stale-build.log"; then
  printf '%s\n' "Stale build failed without the expected operator guidance." >&2
  sed -n '1,160p' "$WORK_DIR/stale-build.log" >&2
  exit 1
fi

printf '%s\n' \
  "Docker artifact freshness proof passed: fresh manifest accepted, stale input rejected."
