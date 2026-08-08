#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly OVERLAY="${K8S_OVERLAY:-}"
readonly KUBE_CONTEXT="${KUBE_CONTEXT:-}"
readonly KUBE_NAMESPACE="${KUBE_NAMESPACE:-}"
readonly CONFIRMATION="${CONFIRM_K8S_ROLLOUT:-}"
readonly TIMEOUT="${K8S_ROLLOUT_TIMEOUT:-10m}"
readonly JWKS_MIGRATION="${JWKS_MIGRATION:-false}"
readonly JWKS_WAIT_SECONDS="${K8S_JWKS_WAIT_SECONDS:-0}"

if [[ -z "$OVERLAY" || -z "$KUBE_CONTEXT" || -z "$KUBE_NAMESPACE" ]]; then
  echo "Set K8S_OVERLAY, KUBE_CONTEXT and KUBE_NAMESPACE explicitly." >&2
  exit 1
fi
if [[ "$CONFIRMATION" != "YES" ]]; then
  echo "Refusing rollout: set CONFIRM_K8S_ROLLOUT=YES after reviewing the rendered overlay." >&2
  exit 1
fi
if [[ ! -f "$OVERLAY/kustomization.yaml" && ! -f "$OVERLAY/kustomization.yml" ]]; then
  echo "K8S_OVERLAY must point to a Kustomize directory: $OVERLAY" >&2
  exit 1
fi
if [[ "$JWKS_MIGRATION" == "true" && "$JWKS_WAIT_SECONDS" -le 0 ]]; then
  echo "JWKS_MIGRATION=true requires K8S_JWKS_WAIT_SECONDS to include access-token TTL and clock skew." >&2
  exit 1
fi

command -v kubectl >/dev/null || { echo "kubectl is required." >&2; exit 1; }
command -v rg >/dev/null || { echo "rg is required for safe overlay checks." >&2; exit 1; }

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT INT TERM

kubectl --context "$KUBE_CONTEXT" kustomize "$OVERLAY" >"$rendered"

if rg -n 'example\.invalid|registry\.example\.invalid|REPLACE_ME|REPLACE_WITH_' "$rendered" >/dev/null; then
  echo "Refusing rollout: rendered overlay still contains a placeholder endpoint/image/value." >&2
  rg -n 'example\.invalid|registry\.example\.invalid|REPLACE_ME|REPLACE_WITH_' "$rendered" >&2
  exit 1
fi
if rg -n '^kind: Secret$|^  type: LoadBalancer$|^kind: NodePort$' "$rendered" >/dev/null; then
  echo "Refusing rollout: plaintext Secret or public service exposure is present in the overlay." >&2
  exit 1
fi
if ! rg -q "^  namespace: ${KUBE_NAMESPACE//./\\.}$" "$rendered"; then
  echo "Refusing rollout: rendered resources do not use KUBE_NAMESPACE=$KUBE_NAMESPACE." >&2
  exit 1
fi

kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" \
  get namespace "$KUBE_NAMESPACE" >/dev/null

apply_wave() {
  local wave="$1"
  echo "JWKS rollout: applying wave ${wave}"
  kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" apply \
    --server-side --field-manager=delivery-rollout \
    --selector "delivery.platform/wave=${wave}" \
    --kustomize "$OVERLAY"
}

wait_for_deployments() {
  local deployment
  for deployment in "$@"; do
    kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" \
      rollout status "deployment/${deployment}" --timeout="$TIMEOUT"
  done
}

apply_wave control
wait_for_deployments config-server discovery-server

apply_wave auth
wait_for_deployments auth-service

# Check Auth's public JWKS shape from inside the Auth pod without printing key
# material. The service image contains wget; the response is only inspected for
# the public keys array and forbidden private-key markers.
if [[ "${VERIFY_JWKS_PUBLIC_SHAPE:-true}" == "true" ]]; then
  auth_pod="$(kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" \
    get pods -l app.kubernetes.io/name=auth-service \
    -o jsonpath='{.items[0].metadata.name}')"
  if [[ -z "$auth_pod" ]]; then
    echo "Auth JWKS check could not find an Auth pod." >&2
    exit 1
  fi
  jwks="$(kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" \
    exec "$auth_pod" -- wget -qO- http://localhost:8081/.well-known/jwks.json)"
  if ! printf '%s' "$jwks" | rg -q '"keys"'; then
    echo "Auth JWKS response does not contain a public keys array." >&2
    exit 1
  fi
  if printf '%s' "$jwks" | rg -qi 'private|BEGIN (RSA )?PRIVATE KEY|"d"'; then
    echo "Auth JWKS response appears to contain private key material." >&2
    exit 1
  fi
fi

if [[ "$JWKS_MIGRATION" == "true" ]]; then
  echo "JWKS rollout: waiting ${JWKS_WAIT_SECONDS}s for access-token TTL plus clock skew"
  sleep "$JWKS_WAIT_SECONDS"
fi

apply_wave resources
wait_for_deployments \
  user-service restaurant-service order-service delivery-service search-service \
  shipper-service settlement-service notification-service match-service tracking-service \
  livestream-service saga-orchestrator-service promotion-service analytics-service flashsale-service

apply_wave gateway
wait_for_deployments api-gateway

echo "PASS: controlled Kubernetes rollout completed control → Auth → resources → Gateway for namespace ${KUBE_NAMESPACE}."
