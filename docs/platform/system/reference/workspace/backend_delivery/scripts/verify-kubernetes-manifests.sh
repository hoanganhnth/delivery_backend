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
bash -n scripts/rollout-identity-principal-kubernetes.sh
bash scripts/verify-identity-explicit-claims.sh

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

# 18 backend application services plus Config Server and Eureka Discovery Server.
# Derive the expected count from the generated inventory so adding an internal
# service cannot silently make this deployment preflight stale.
expected_workloads="$(find deploy/kubernetes/base/generated -type f -name '*.deployment.yaml' | wc -l | tr -d ' ')"
require_count "$expected_workloads" '^kind: Deployment$' "$base_rendered" 'Deployments'
require_count "$expected_workloads" '^kind: Service$' "$base_rendered" 'ClusterIP Services'
require_count "$expected_workloads" '^kind: ServiceAccount$' "$base_rendered" 'ServiceAccounts'
require_count "$expected_workloads" '^        readinessProbe:$' "$base_rendered" 'readiness probes'
require_count "$expected_workloads" '^        livenessProbe:$' "$base_rendered" 'liveness probes'
require_count "$expected_workloads" '^        startupProbe:$' "$base_rendered" 'startup probes'
require_count "$expected_workloads" '^          runAsNonRoot: true$' "$base_rendered" 'non-root container declarations'
require_count "$expected_workloads" '^          readOnlyRootFilesystem: true$' "$base_rendered" 'read-only root filesystem declarations'
require_count "$expected_workloads" '^        image: registry\.example\.invalid/delivery/' "$base_rendered" 'safe placeholder images'
# Each generated workload contributes one wave label to its Deployment,
# ServiceAccount and Service; the three base ConfigMaps carry the control-wave
# label as well.
require_count "$((expected_workloads * 3 + 3))" '^    delivery\.platform/wave:' "$base_rendered" 'wave labels'

require_absent '^kind: Ingress$' "$base_rendered" 'Ingress'
require_absent '^  type: LoadBalancer$' "$base_rendered" 'LoadBalancer Service'
require_absent '^  type: NodePort$' "$base_rendered" 'NodePort Service'
require_absent '^kind: Secret$' "$base_rendered" 'plaintext Secret'

if ! rg -Fq 'type: (LoadBalancer|NodePort)' scripts/rollout-kubernetes.sh; then
  echo "Kubernetes manifest verification: rollout guard must reject both LoadBalancer and NodePort Services." >&2
  exit 1
fi
if ! rg -Fq 'CONFIRM_IDENTITY_ROLLOUT=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_SHIPPER_PROJECTION_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_REGISTRATION_DRAINED=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_RESTAURANT_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_ORDER_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_NOTIFICATION_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_SETTLEMENT_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_PROMOTION_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_FLASHSALE_PRINCIPAL_PARITY=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_SUBJECT_CUTOVER=YES' scripts/rollout-identity-principal-kubernetes.sh \
  || ! rg -Fq 'CONFIRM_EXPLICIT_CLAIM_AUDIT=YES' scripts/rollout-identity-principal-kubernetes.sh; then
  echo "Kubernetes manifest verification: identity rollout runner must retain explicit mutation and projection-parity confirmations." >&2
  exit 1
fi

# T2 customer admission must not be opened in the middle of T1. Keep the
# dependency visible in static verification: profile relay, both projections,
# bootstrap and the Auth status relay have to be on before its ConfigMap patch.
admission_guard="$(awk '/^r3_admission\(\)/,/^r2_user_consumer\(\)/' scripts/rollout-identity-principal-kubernetes.sh)"
for prerequisite in \
  'require_value AUTH_IDENTITY_EVENTS_ENABLED true' \
  'require_value USER_IDENTITY_OUTBOX_RELAY_ENABLED true' \
  'require_value USER_IDENTITY_EVENTS_ENABLED true' \
  'require_value SHIPPER_IDENTITY_EVENTS_ENABLED true' \
  'require_value AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED true' \
  'require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED true'; do
  if ! printf '%s\n' "$admission_guard" | grep -Fq "$prerequisite"; then
    echo "Kubernetes manifest verification: r3-admission must require complete T1 identity lifecycle plumbing (${prerequisite})." >&2
    exit 1
  fi
done

if ! grep -q '^  namespace: delivery$' "$base_rendered"; then
  echo "Kubernetes manifest verification: base render must remain in the delivery namespace." >&2
  exit 1
fi
if ! grep -q '^  namespace: delivery-staging$' "$staging_rendered"; then
  echo "Kubernetes manifest verification: staging template must set delivery-staging namespace." >&2
  exit 1
fi

for expected in \
  'name: IDENTITY_EVENTS_ENABLED' \
  'key: AUTH_IDENTITY_EVENTS_ENABLED' \
  'key: AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED' \
  'key: AUTH_PUBLIC_REGISTRATION_ENABLED' \
  'key: AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE' \
  'key: AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE' \
  'key: USER_IDENTITY_EVENTS_ENABLED' \
  'key: SHIPPER_IDENTITY_EVENTS_ENABLED' \
  'key: DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED' \
  'key: TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED' \
  'key: RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED' \
  'key: ORDER_PRINCIPAL_OWNERSHIP_ENFORCED' \
  'key: NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED' \
  'key: SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED' \
  'key: PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED' \
  'key: FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED'; do
  if ! grep -Fq "$expected" "$base_rendered"; then
    echo "Kubernetes manifest verification: missing service-scoped identity rollout control $expected." >&2
    exit 1
  fi
done

# Voucher stacking is a separate rollout gate from legacy voucher checkout.
# Keep the base fail-closed and require both service-specific switches plus
# their stable-principal allowlists to be present before an overlay can be
# reviewed for activation.
for expected in \
  'ORDER_VOUCHER_STACKING_ENABLED: "false"' \
  'ORDER_VOUCHER_STACKING_CANARY_PRINCIPALS: ""' \
  'PROMOTION_STACKING_ENABLED: "false"' \
  'PROMOTION_STACKING_CANARY_PRINCIPALS: ""'; do
  if ! grep -Fq "$expected" "$base_rendered"; then
    echo "Kubernetes manifest verification: voucher stacking rollout control is missing or not fail-closed ($expected)." >&2
    exit 1
  fi
done

# Every identity capability flag must enter exactly its owning workload. This
# catches a generator/config ref regression before a ConfigMap update could
# restart one service but silently activate behaviour in another one.
generated_dir='deploy/kubernetes/base/generated'
assert_owned_runtime_key() {
  local owner_file="$1" key="$2" matches
  matches="$(rg -l -F "\"key\": \"${key}\"" "${generated_dir}"/*.deployment.yaml || true)"
  if [[ "$matches" != "${generated_dir}/${owner_file}.deployment.yaml" ]]; then
    echo "Kubernetes manifest verification: ${key} must map only to ${owner_file}; found ${matches:-<none>}." >&2
    exit 1
  fi
}

assert_owned_runtime_key auth-service AUTH_IDENTITY_EVENTS_ENABLED
assert_owned_runtime_key auth-service AUTH_IDENTITY_OUTBOX_RELAY_ENABLED
assert_owned_runtime_key auth-service AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED
assert_owned_runtime_key auth-service AUTH_PUBLIC_REGISTRATION_ENABLED
assert_owned_runtime_key auth-service AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE
assert_owned_runtime_key auth-service AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE
assert_owned_runtime_key user-service USER_IDENTITY_EVENTS_ENABLED
assert_owned_runtime_key user-service USER_IDENTITY_OUTBOX_RELAY_ENABLED
assert_owned_runtime_key shipper-service SHIPPER_IDENTITY_EVENTS_ENABLED
assert_owned_runtime_key shipper-service SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED
assert_owned_runtime_key delivery-service DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED
assert_owned_runtime_key tracking-service TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED
assert_owned_runtime_key restaurant-service RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED
assert_owned_runtime_key order-service ORDER_PRINCIPAL_OWNERSHIP_ENFORCED
assert_owned_runtime_key notification-service NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED
assert_owned_runtime_key settlement-service SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED
assert_owned_runtime_key promotion-service PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED
assert_owned_runtime_key flashsale-service FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED

echo "PASS: Kubernetes base and staging template rendered with ${expected_workloads} private workloads, probes, non-root security context and no public edge/secret material."
