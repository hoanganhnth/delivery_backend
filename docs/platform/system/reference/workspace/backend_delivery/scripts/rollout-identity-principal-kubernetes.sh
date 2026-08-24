#!/usr/bin/env bash
set -euo pipefail

# Executes one narrowly-scoped runtime switch for the identity/principal
# migration. It deliberately does not run tests, create traffic, query
# Prometheus, or apply a complete Kustomize overlay. The operator owns those
# gates and must first make the same ConfigMap change durable in the private
# GitOps overlay, otherwise its next reconciliation could undo this command.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PHASE="${1:-}"
readonly KUBE_CONTEXT="${KUBE_CONTEXT:-}"
readonly KUBE_NAMESPACE="${KUBE_NAMESPACE:-}"
readonly CONFIRMATION="${CONFIRM_IDENTITY_ROLLOUT:-}"
readonly TIMEOUT="${IDENTITY_ROLLOUT_TIMEOUT:-10m}"
readonly CONFIG_MAP="${IDENTITY_RUNTIME_CONFIG_MAP:-delivery-runtime}"
readonly CANARY_PERCENTAGE="${IDENTITY_REGISTRATION_CANARY_PERCENTAGE:-}"
readonly CANARY_SECRET="${IDENTITY_REGISTRATION_CANARY_SECRET:-delivery-auth-registration-canary}"

readonly -a ALL_FLAGS=(
  AUTH_IDENTITY_EVENTS_ENABLED
  AUTH_IDENTITY_OUTBOX_RELAY_ENABLED
  AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED
  AUTH_PUBLIC_REGISTRATION_ENABLED
  AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE
  AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE
  USER_IDENTITY_EVENTS_ENABLED
  USER_IDENTITY_OUTBOX_RELAY_ENABLED
  SHIPPER_IDENTITY_EVENTS_ENABLED
  SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED
  DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED
  TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED
  RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED
  ORDER_PRINCIPAL_OWNERSHIP_ENFORCED
  NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED
  SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED
  PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED
  FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED
)

# Runtime counterpart to the generated-manifest check. A private overlay can
# replace a Deployment, so preflight proves the selected cluster still injects
# every control into its intended process before patching the shared ConfigMap.
# Format: deployment|application environment variable|ConfigMap key.
readonly -a RUNTIME_BINDINGS=(
  'auth-service|IDENTITY_EVENTS_ENABLED|AUTH_IDENTITY_EVENTS_ENABLED'
  'auth-service|IDENTITY_OUTBOX_RELAY_ENABLED|AUTH_IDENTITY_OUTBOX_RELAY_ENABLED'
  'auth-service|IDENTITY_STATUS_BOOTSTRAP_ENABLED|AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED'
  'auth-service|PUBLIC_REGISTRATION_ENABLED|AUTH_PUBLIC_REGISTRATION_ENABLED'
  'auth-service|REGISTRATION_CANARY_PERCENTAGE|AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE'
  'auth-service|JWT_ACCESS_TOKEN_SUBJECT_MODE|AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE'
  'user-service|IDENTITY_EVENTS_ENABLED|USER_IDENTITY_EVENTS_ENABLED'
  'user-service|IDENTITY_OUTBOX_RELAY_ENABLED|USER_IDENTITY_OUTBOX_RELAY_ENABLED'
  'shipper-service|IDENTITY_EVENTS_ENABLED|SHIPPER_IDENTITY_EVENTS_ENABLED'
  'shipper-service|SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED|SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED'
  'delivery-service|SHIPPER_IDENTITY_PROJECTION_ENFORCED|DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED'
  'tracking-service|SHIPPER_IDENTITY_PROJECTION_ENFORCED|TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED'
  'restaurant-service|RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED|RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED'
  'order-service|ORDER_PRINCIPAL_OWNERSHIP_ENFORCED|ORDER_PRINCIPAL_OWNERSHIP_ENFORCED'
  'notification-service|NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED|NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED'
  'settlement-service|SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED|SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED'
  'promotion-service|PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED|PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED'
  'flashsale-service|FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED|FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED'
)

die() { printf 'Identity rollout: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage:
  KUBE_CONTEXT=... KUBE_NAMESPACE=... \
    bash scripts/rollout-identity-principal-kubernetes.sh preflight|status

  KUBE_CONTEXT=... KUBE_NAMESPACE=... CONFIRM_IDENTITY_ROLLOUT=YES \
    bash scripts/rollout-identity-principal-kubernetes.sh <phase>

Mutating phases (each patches delivery-runtime and restarts only the owner):
  r1-auth-consumer       Enable Auth consumption of identity.profile.created.
  r1-user-relay          Enable User relay of identity.profile.created.
  r3-admission           Enable Auth registration gate and set a cohort.
                         Requires IDENTITY_REGISTRATION_CANARY_PERCENTAGE=0..100.
  r1-admission           Deprecated compatibility alias for r3-admission.
  r2-user-consumer       Enable the User status consumer only.
  r2-shipper-consumer    Enable the Shipper status consumer only.
  r2-consumers           Compatibility alias: enable both R2 consumers.
  r2-status-bootstrap    Emit one Auth lifecycle snapshot per existing linked
                         account; drain its pending gauge before Auth relay.
  r2-auth-relay          Enable Auth identity.status.changed relay.
  r4-shipper-relay       Enable Shipper principal->shipper mapping relay.
  r4-tracking-enforce    Enforce Tracking mapping; requires
                         CONFIRM_SHIPPER_PROJECTION_PARITY=YES.
  r4-delivery-enforce    Enforce Delivery mapping after Tracking; requires
                         CONFIRM_SHIPPER_PROJECTION_PARITY=YES.
  r4-restaurant-enforce  Enforce Restaurant owner principal ownership; requires
                         CONFIRM_RESTAURANT_PRINCIPAL_PARITY=YES.
  r4-order-enforce       Enforce Order customer/restaurant principal ownership;
                         requires Restaurant enforcement and
                         CONFIRM_ORDER_PRINCIPAL_PARITY=YES.
  r4-notification-enforce Enforce Notification inbox principal ownership;
                         requires CONFIRM_NOTIFICATION_PRINCIPAL_PARITY=YES.
  r4-settlement-enforce  Enforce Settlement refund principal ownership;
                         requires CONFIRM_SETTLEMENT_PRINCIPAL_PARITY=YES.
  r4-promotion-enforce   Enforce Promotion wallet principal ownership;
                         requires CONFIRM_PROMOTION_PRINCIPAL_PARITY=YES.
  r4-flashsale-enforce   Enforce Flash Sale reservation principal ownership;
                         requires CONFIRM_FLASHSALE_PRINCIPAL_PARITY=YES.
  r5-subject-principal   Auth issues access JWT sub=principal_id.
                         Requires CONFIRM_SUBJECT_CUTOVER=YES and
                         CONFIRM_EXPLICIT_CLAIM_AUDIT=YES.
  r3-shipper-relay, r3-tracking-enforce, r3-delivery-enforce
                         Deprecated aliases for the R4 commands above.
  close-registration     Set cohort 0; set IDENTITY_CLOSE_ALLOWLIST=true to
                         also turn the Auth registration master switch off.
  rollback-status        Stop Auth status relay; retain the proven R1 profile
                         path and public registration admission.
  rollback-profile       Close registration, then disable the R1/R2 event path.
                         Requires CONFIRM_REGISTRATION_DRAINED=YES after the
                         User profile outbox/lag/DLT gate is recorded.
  rollback-shipper       Delivery/Tracking enforcement off -> Shipper relay off.
  rollback-restaurant-order Order enforcement off -> Restaurant enforcement off.
  rollback-notification  Notification principal ownership enforcement off.
  rollback-settlement    Settlement principal ownership enforcement off.
  rollback-promotion     Promotion principal ownership enforcement off.
  rollback-flashsale     Flash Sale principal ownership enforcement off.
  rollback-subject-legacy Revert Auth issuer to sub=legacy_user_id; use only
                         during the documented TTL observation window.

Safety:
  - Before a mutating command, update and review the same key in the private
    GitOps/Kustomize overlay. This command is an imperative, audited bridge;
    it must not race a reconciler that will restore an older value.
  - Preflight reads the selected Deployment specs and refuses a target whose
    environment does not map every identity key to delivery-runtime exactly as
    this runner expects. It does not trust a local rendered manifest alone.
  - `r3-admission` below 100 requires the Auth-only secret
    delivery-auth-registration-canary with keys `allowlist` and `hash-key`.
  - After every phase, stop and inspect outbox age, Kafka lag/retry/DLT and
    the registration/fallback dashboard before invoking the next phase.
  - Only `r5-subject-principal` changes JWT `sub`. It is Auth-only and does
    not wait for R4 ownership backfills: resource boundaries must already use
    explicit claims. Confirm the recorded HTTP/WebSocket audit and healthy
    identity outbox/lag/retry/DLT/error signals before invoking it.
EOF
}

require_commands() {
  command -v kubectl >/dev/null || die 'kubectl is required.'
  command -v date >/dev/null || die 'date is required.'
}

kubectl_ns() { kubectl --context "$KUBE_CONTEXT" --namespace "$KUBE_NAMESPACE" "$@"; }

require_runtime_binding() {
  local deployment="$1" environment="$2" config_key="$3" binding
  binding="$(kubectl_ns get "deployment/${deployment}" \
    -o jsonpath="{range .spec.template.spec.containers[*].env[?(@.name==\"${environment}\")]}{.valueFrom.configMapKeyRef.name}:{.valueFrom.configMapKeyRef.key}{\"\\n\"}{end}")"
  [[ "$binding" == "${CONFIG_MAP}:${config_key}" ]] || die \
    "Deployment ${deployment} must map ${environment} only to ${CONFIG_MAP}/${config_key}; observed ${binding:-<missing>}. Deploy/review T0 overlay first."
}

require_target() {
  [[ -n "$KUBE_CONTEXT" && -n "$KUBE_NAMESPACE" ]] \
    || die 'Set KUBE_CONTEXT and KUBE_NAMESPACE explicitly.'
  [[ "$TIMEOUT" =~ ^[0-9]+[smh]$ ]] || die 'IDENTITY_ROLLOUT_TIMEOUT must look like 10m.'
  kubectl_ns get namespace "$KUBE_NAMESPACE" >/dev/null
  kubectl_ns get configmap "$CONFIG_MAP" >/dev/null
  for deployment in \
    auth-service user-service shipper-service delivery-service tracking-service \
    restaurant-service order-service notification-service settlement-service \
    promotion-service flashsale-service; do
    kubectl_ns get "deployment/${deployment}" >/dev/null
  done
  local key binding deployment environment
  for key in "${ALL_FLAGS[@]}"; do
    [[ -n "$(config_value "$key")" ]] \
      || die "ConfigMap ${CONFIG_MAP} is missing ${key}. Deploy R0 source first."
  done
  for binding in "${RUNTIME_BINDINGS[@]}"; do
    IFS='|' read -r deployment environment key <<< "$binding"
    require_runtime_binding "$deployment" "$environment" "$key"
  done
}

require_confirmation() {
  [[ "$CONFIRMATION" == 'YES' ]] || die \
    'Refusing mutation: set CONFIRM_IDENTITY_ROLLOUT=YES after reviewing the private overlay and runtime gates.'
}

config_value() {
  local key="$1"
  kubectl_ns get configmap "$CONFIG_MAP" -o "jsonpath={.data.${key}}"
}

require_value() {
  local key="$1" expected="$2" actual
  actual="$(config_value "$key")"
  [[ "$actual" == "$expected" ]] || die "Required ${key}=${expected}; observed ${actual:-<empty>}."
}

set_flags() {
  local owner="$1"; shift
  local patch='{ "data": {' separator='' key value current changed=false revision
  (( $# % 2 == 0 )) || die 'set_flags expects key/value pairs.'
  while (( $# > 0 )); do
    key="$1"; value="$2"; shift 2
    current="$(config_value "$key")"
    if [[ "$current" == "$value" ]]; then
      printf 'Identity rollout: %s already %s.\n' "$key" "$value"
      continue
    fi
    patch+="${separator}\"${key}\":\"${value}\""
    separator=,
    changed=true
  done
  [[ "$changed" == true ]] || { printf 'Identity rollout: no restart for %s.\n' "$owner"; return; }
  patch+='}}'
  kubectl_ns patch configmap "$CONFIG_MAP" --type merge -p "$patch" >/dev/null
  revision="identity-${owner}-$(date -u +%Y%m%dT%H%M%SZ)"
  kubectl_ns annotate "deployment/${owner}" \
    "delivery.platform/identity-config-revision=${revision}" --overwrite >/dev/null
  kubectl_ns rollout status "deployment/${owner}" --timeout="$TIMEOUT"
  printf 'Identity rollout: configuration applied; %s is ready.\n' "$owner"
}

assert_all_disabled() {
  local key expected
  for key in "${ALL_FLAGS[@]}"; do
    expected=false
    [[ "$key" == AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE ]] && expected=0
    [[ "$key" == AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE ]] && expected=LEGACY_USER_ID
    require_value "$key" "$expected"
  done
}

require_canary_secret() {
  local key
  kubectl_ns get secret "$CANARY_SECRET" >/dev/null \
    || die "Partial cohort requires Auth-only secret ${CANARY_SECRET}."
  for key in allowlist hash-key; do
    kubectl_ns get secret "$CANARY_SECRET" -o "jsonpath={.data.${key}}" | grep -q . \
      || die "Partial cohort secret ${CANARY_SECRET} is missing key ${key}."
  done
}

status() {
  require_target
  printf 'Identity rollout target: context=%s namespace=%s configmap=%s\n' "$KUBE_CONTEXT" "$KUBE_NAMESPACE" "$CONFIG_MAP"
  for key in "${ALL_FLAGS[@]}"; do printf '  %s=%s\n' "$key" "$(config_value "$key")"; done
  printf 'Deployments:\n'
  kubectl_ns get deployments auth-service user-service shipper-service tracking-service delivery-service
  printf '%s\n' 'Next gate is external: inspect outbox age, consumer lag/retry/DLT, registration admission, HTTP errors and fallback metrics.'
}

preflight() {
  require_target
  printf '%s\n' 'Identity rollout preflight passed. This command made no changes.'
  status
}

r1_auth_consumer() {
  assert_all_disabled
  set_flags auth-service AUTH_IDENTITY_EVENTS_ENABLED true
}

r1_user_relay() {
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false
  require_value AUTH_PUBLIC_REGISTRATION_ENABLED false
  require_value USER_IDENTITY_EVENTS_ENABLED false
  set_flags user-service USER_IDENTITY_OUTBOX_RELAY_ENABLED true
}

r3_admission() {
  [[ "$CANARY_PERCENTAGE" =~ ^(0|[1-9][0-9]?|100)$ ]] \
    || die 'Set IDENTITY_REGISTRATION_CANARY_PERCENTAGE to an integer from 0 through 100.'
  # T2 only admits customer traffic after T1 has made both profile and
  # lifecycle flows durable. This avoids a registration cohort whose profile
  # completes while status projection/relay is still unavailable.
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_OUTBOX_RELAY_ENABLED true
  require_value USER_IDENTITY_EVENTS_ENABLED true
  require_value SHIPPER_IDENTITY_EVENTS_ENABLED true
  require_value AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED true
  # At 0%, admission is allowlist-only; at 1..99%, it is allowlist plus a
  # keyed deterministic cohort. Both require the Auth-only secret. A complete
  # 100% release intentionally does not depend on that optional secret.
  if (( CANARY_PERCENTAGE < 100 )); then require_canary_secret; fi
  set_flags auth-service \
    AUTH_PUBLIC_REGISTRATION_ENABLED true \
    AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE "$CANARY_PERCENTAGE"
}

r2_user_consumer() {
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_OUTBOX_RELAY_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false
  set_flags user-service USER_IDENTITY_EVENTS_ENABLED true
}

r2_shipper_consumer() {
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_OUTBOX_RELAY_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false
  set_flags shipper-service SHIPPER_IDENTITY_EVENTS_ENABLED true
}

r2_consumers() {
  # Compatibility bridge for an already-written operator command. New
  # rollouts use the two owner-scoped commands above and inspect each group
  # before starting the next cell.
  r2_user_consumer
  r2_shipper_consumer
}

r2_auth_relay() {
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_EVENTS_ENABLED true
  require_value SHIPPER_IDENTITY_EVENTS_ENABLED true
  require_value AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED true
  set_flags auth-service AUTH_IDENTITY_OUTBOX_RELAY_ENABLED true
}

r2_status_bootstrap() {
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_EVENTS_ENABLED true
  require_value SHIPPER_IDENTITY_EVENTS_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false
  set_flags auth-service AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED true
}

r4_shipper_relay() {
  set_flags shipper-service SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED true
}

require_projection_parity() {
  [[ "${CONFIRM_SHIPPER_PROJECTION_PARITY:-}" == YES ]] || die \
    'Refusing enforcement: set CONFIRM_SHIPPER_PROJECTION_PARITY=YES only after projection count parity, zero lag/retry/DLT and missing-principal remediation are recorded.'
  require_value SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED true
}

r4_tracking_enforce() {
  require_projection_parity
  set_flags tracking-service TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED true
}

r4_delivery_enforce() {
  require_projection_parity
  require_value TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED true
  set_flags delivery-service DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED true
}

r4_restaurant_enforce() {
  [[ "${CONFIRM_RESTAURANT_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Restaurant enforcement: set CONFIRM_RESTAURANT_PRINCIPAL_PARITY=YES only after local principal backfill/remediation, zero fallback and no relevant errors are recorded.'
  set_flags restaurant-service RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r4_order_enforce() {
  [[ "${CONFIRM_ORDER_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Order enforcement: set CONFIRM_ORDER_PRINCIPAL_PARITY=YES only after local customer/owner backfill/remediation, zero fallback and no relevant errors are recorded.'
  require_value RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED true
  set_flags order-service ORDER_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r4_notification_enforce() {
  [[ "${CONFIRM_NOTIFICATION_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Notification enforcement: set CONFIRM_NOTIFICATION_PRINCIPAL_PARITY=YES only after local backfill/remediation, seven days zero fallback and no inbox error regression are recorded.'
  set_flags notification-service NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r4_settlement_enforce() {
  [[ "${CONFIRM_SETTLEMENT_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Settlement enforcement: set CONFIRM_SETTLEMENT_PRINCIPAL_PARITY=YES only after local refund-case backfill/remediation, seven days zero fallback and no self-service error regression are recorded.'
  set_flags settlement-service SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r4_promotion_enforce() {
  [[ "${CONFIRM_PROMOTION_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Promotion enforcement: set CONFIRM_PROMOTION_PRINCIPAL_PARITY=YES only after local wallet/reservation remediation, seven days zero fallback and checkout error gate are recorded.'
  set_flags promotion-service PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r4_flashsale_enforce() {
  [[ "${CONFIRM_FLASHSALE_PRINCIPAL_PARITY:-}" == YES ]] || die \
    'Refusing Flash Sale enforcement: set CONFIRM_FLASHSALE_PRINCIPAL_PARITY=YES only after local reservation remediation, seven days zero fallback and checkout error gate are recorded.'
  set_flags flashsale-service FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED true
}

r5_subject_principal() {
  [[ "${CONFIRM_SUBJECT_CUTOVER:-}" == YES ]] || die \
    'Refusing subject cutover: set CONFIRM_SUBJECT_CUTOVER=YES after the approved Auth-only R5 change record.'
  [[ "${CONFIRM_EXPLICIT_CLAIM_AUDIT:-}" == YES ]] || die \
    'Refusing subject cutover: set CONFIRM_EXPLICIT_CLAIM_AUDIT=YES only after every deployed HTTP/WebSocket resource boundary is recorded as using principal_id and legacy_user_id, with healthy Track A outbox/lag/retry/DLT/error signals.'
  # The source/image revision must pass this deterministic half of the audit;
  # the separate confirmation records the runtime half (metrics and all
  # deployed boundaries). R4 ownership flags deliberately do not appear here.
  bash "${ROOT_DIR}/scripts/verify-identity-explicit-claims.sh"
  require_value AUTH_IDENTITY_EVENTS_ENABLED true
  require_value USER_IDENTITY_OUTBOX_RELAY_ENABLED true
  require_value USER_IDENTITY_EVENTS_ENABLED true
  require_value SHIPPER_IDENTITY_EVENTS_ENABLED true
  require_value AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED true
  require_value AUTH_IDENTITY_OUTBOX_RELAY_ENABLED true
  require_value AUTH_PUBLIC_REGISTRATION_ENABLED true
  require_value AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE 100
  require_value AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE LEGACY_USER_ID
  set_flags auth-service AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE PRINCIPAL_ID
}

close_registration() {
  if [[ "${IDENTITY_CLOSE_ALLOWLIST:-false}" == true ]]; then
    set_flags auth-service \
      AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE 0 \
      AUTH_PUBLIC_REGISTRATION_ENABLED false
    return
  fi
  set_flags auth-service AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE 0
}

rollback_status() {
  set_flags auth-service AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false
}

rollback_profile() {
  [[ "${CONFIRM_REGISTRATION_DRAINED:-}" == YES ]] || die \
    'Refusing profile-path rollback: close registration, verify accepted registrations have drained through User outbox/Auth consumer with zero lag/DLT, then set CONFIRM_REGISTRATION_DRAINED=YES.'
  set_flags auth-service \
    AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE 0 \
    AUTH_PUBLIC_REGISTRATION_ENABLED false \
    AUTH_IDENTITY_OUTBOX_RELAY_ENABLED false \
    AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED false \
    AUTH_IDENTITY_EVENTS_ENABLED false
  set_flags user-service USER_IDENTITY_EVENTS_ENABLED false
  set_flags shipper-service SHIPPER_IDENTITY_EVENTS_ENABLED false
  set_flags user-service USER_IDENTITY_OUTBOX_RELAY_ENABLED false
}

rollback_shipper() {
  set_flags delivery-service DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED false
  set_flags tracking-service TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED false
  set_flags shipper-service SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED false
}

rollback_restaurant_order() {
  set_flags order-service ORDER_PRINCIPAL_OWNERSHIP_ENFORCED false
  set_flags restaurant-service RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED false
}

rollback_notification() {
  set_flags notification-service NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED false
}

rollback_settlement() {
  set_flags settlement-service SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED false
}

rollback_promotion() {
  set_flags promotion-service PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED false
}

rollback_flashsale() {
  set_flags flashsale-service FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED false
}

rollback_subject_legacy() {
  require_value AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE PRINCIPAL_ID
  set_flags auth-service AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE LEGACY_USER_ID
}

case "$PHASE" in
  help|-h|--help|'') usage ;;
  preflight) require_commands; preflight ;;
  status) require_commands; status ;;
  *)
    require_commands
    require_target
    require_confirmation
    case "$PHASE" in
      r1-auth-consumer) r1_auth_consumer ;;
      r1-user-relay) r1_user_relay ;;
      r1-admission|r3-admission) r3_admission ;;
      r2-consumers) r2_consumers ;;
      r2-status-bootstrap) r2_status_bootstrap ;;
      r2-auth-relay) r2_auth_relay ;;
      r2-user-consumer) r2_user_consumer ;;
      r2-shipper-consumer) r2_shipper_consumer ;;
      r3-shipper-relay|r4-shipper-relay) r4_shipper_relay ;;
      r3-tracking-enforce|r4-tracking-enforce) r4_tracking_enforce ;;
      r3-delivery-enforce|r4-delivery-enforce) r4_delivery_enforce ;;
      r4-restaurant-enforce) r4_restaurant_enforce ;;
      r4-order-enforce) r4_order_enforce ;;
      r4-notification-enforce) r4_notification_enforce ;;
      r4-settlement-enforce) r4_settlement_enforce ;;
      r4-promotion-enforce) r4_promotion_enforce ;;
      r4-flashsale-enforce) r4_flashsale_enforce ;;
      r5-subject-principal) r5_subject_principal ;;
      close-registration) close_registration ;;
      rollback-status) rollback_status ;;
      rollback-profile) rollback_profile ;;
      rollback-shipper) rollback_shipper ;;
      rollback-restaurant-order) rollback_restaurant_order ;;
      rollback-notification) rollback_notification ;;
      rollback-settlement) rollback_settlement ;;
      rollback-promotion) rollback_promotion ;;
      rollback-flashsale) rollback_flashsale ;;
      rollback-subject-legacy) rollback_subject_legacy ;;
      *) usage >&2; die "Unknown phase: ${PHASE}" ;;
    esac
    ;;
esac
