#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SERVICE_PATH="${SERVICE_PATH:-api-gateway}"
readonly RUN_ID="$(date +%s)-$$"
readonly IMAGE_TAG="delivery-runtime-security-check:${RUN_ID}"

if [[ ! -f "${ROOT_DIR}/${SERVICE_PATH}/pom.xml" ]]; then
  echo "Unknown service path: ${SERVICE_PATH}" >&2
  exit 1
fi
if ! compgen -G "${ROOT_DIR}/${SERVICE_PATH}/target/*.jar" >/dev/null; then
  echo "${SERVICE_PATH}: packaged JAR is required; run Maven package first." >&2
  exit 1
fi

cleanup() {
  docker image rm --force "$IMAGE_TAG" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker build --quiet --tag "$IMAGE_TAG" --build-arg SERVICE_PATH="$SERVICE_PATH" \
  "$ROOT_DIR" >/dev/null

image_user="$(docker image inspect "$IMAGE_TAG" --format '{{.Config.User}}')"
if [[ "$image_user" != "10001:10001" ]]; then
  echo "Runtime image must set USER 10001:10001; found '${image_user:-empty}'." >&2
  exit 1
fi

docker run --rm --read-only --tmpfs /tmp --entrypoint sh "$IMAGE_TAG" -c '
  test "$(id -u)" = 10001
  test -r /app/app.jar
  test ! -w /app
  touch /tmp/runtime-security-smoke
  test -f /tmp/runtime-security-smoke
'

echo "Docker runtime security proof passed for ${SERVICE_PATH}: non-root user, read-only app filesystem and writable /tmp."
