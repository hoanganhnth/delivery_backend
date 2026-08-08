#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

command -v node >/dev/null || {
  echo "Kubernetes manifest verification: node is required." >&2
  exit 1
}
command -v kubectl >/dev/null || {
  echo "Kubernetes manifest verification: kubectl with Kustomize is required." >&2
  exit 1
}

node deploy/kubernetes/generate.mjs --check
bash -n scripts/rollout-kubernetes.sh

base_rendered="$(mktemp)"
staging_rendered="$(mktemp)"
trap 'rm -f "$base_rendered" "$staging_rendered"' EXIT

kubectl kustomize deploy/kubernetes/base >"$base_rendered"
kubectl kustomize deploy/kubernetes/overlays/staging-template >"$staging_rendered"

count_matches() {
  local pattern="$1"
  local file="$2"
  grep -Ec "$pattern" "$file" || true
}

require_count() {
  local expected="$1"
  local pattern="$2"
  local file="$3"
  local label="$4"
  local actual
  actual="$(count_matches "$pattern" "$file")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Kubernetes manifest verification: expected ${expected} ${label}, found ${actual}." >&2
    exit 1
  fi
}

require_absent() {
  local pattern="$1"
  local file="$2"
  local label="$3"
  if grep -Eq "$pattern" "$file"; then
    echo "Kubernetes manifest verification: ${label} must be absent from the render." >&2
    exit 1
  fi
}

# 17 backend application services plus Config Server and Eureka Discovery Server.
require_count 19 '^kind: Deployment$' "$base_rendered" 'Deployments'
require_count 19 '^kind: Service$' "$base_rendered" 'ClusterIP Services'
require_count 19 '^kind: ServiceAccount$' "$base_rendered" 'ServiceAccounts'
require_count 19 '^        readinessProbe:$' "$base_rendered" 'readiness probes'
require_count 19 '^        livenessProbe:$' "$base_rendered" 'liveness probes'
require_count 19 '^        startupProbe:$' "$base_rendered" 'startup probes'
require_count 19 '^          runAsNonRoot: true$' "$base_rendered" 'non-root container declarations'
require_count 19 '^          readOnlyRootFilesystem: true$' "$base_rendered" 'read-only root filesystem declarations'
require_count 19 '^        image: registry\.example\.invalid/delivery/' "$base_rendered" 'safe placeholder images'
require_count 60 '^    delivery\.platform/wave:' "$base_rendered" 'wave labels'

require_absent '^kind: Ingress$' "$base_rendered" 'Ingress'
require_absent '^  type: LoadBalancer$' "$base_rendered" 'LoadBalancer Service'
require_absent '^kind: Secret$' "$base_rendered" 'plaintext Secret'

if ! grep -q '^  namespace: delivery$' "$base_rendered"; then
  echo "Kubernetes manifest verification: base render must remain in the delivery namespace." >&2
  exit 1
fi
if ! grep -q '^  namespace: delivery-staging$' "$staging_rendered"; then
  echo "Kubernetes manifest verification: staging template must set delivery-staging namespace." >&2
  exit 1
fi

echo "PASS: Kubernetes base and staging template rendered with 19 private workloads, probes, non-root security context and no public edge/secret material."
