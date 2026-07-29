#!/usr/bin/env bash
# =============================================================================
# Sinh cặp khóa RSA cho JWT (ký ở auth-service, verify ở api-gateway).
# Format khớp code loader:
#   - private.pem: PKCS#8  (BEGIN PRIVATE KEY)  -> PKCS8EncodedKeySpec
#   - public.pem : X.509   (BEGIN PUBLIC KEY)   -> X509EncodedKeySpec
#
# Khóa KHÔNG được commit (đã .gitignore). Chạy script này:
#   - lần đầu sau khi clone (để build/chạy được), HOẶC
#   - khi cần rotate khóa (khóa cũ coi như đã lộ vì từng nằm trong git history).
#
# Chạy: bash scripts/gen-keys.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # về thư mục backend_delivery

command -v openssl >/dev/null
command -v cmp >/dev/null

BITS="${BITS:-2048}"
AUTH_RES="auth-service/src/main/resources"
GW_RES="api-gateway/src/main/resources"
ENV_FILE=".env"
ROTATE_JWT_KEYS="${ROTATE_JWT_KEYS:-false}"

TMP_PRIV="$(mktemp)"; TMP_PUB="$(mktemp)"
trap 'rm -f "$TMP_PRIV" "$TMP_PUB"' EXIT

# auth-service cần cả private + public; gateway chỉ cần public.
mkdir -p "$AUTH_RES" "$GW_RES"
keys_are_valid=false
if [[ -f "$AUTH_RES/private.pem" && -f "$AUTH_RES/public.pem" && -f "$GW_RES/public.pem" ]] \
    && openssl pkey -in "$AUTH_RES/private.pem" -pubout -outform PEM 2>/dev/null \
      | cmp -s - "$AUTH_RES/public.pem" \
    && cmp -s "$AUTH_RES/public.pem" "$GW_RES/public.pem"; then
  keys_are_valid=true
fi

if [[ "$ROTATE_JWT_KEYS" == "true" || "$keys_are_valid" != "true" ]]; then
  echo "🔑 Sinh khóa RSA $BITS-bit ..."
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:"$BITS" -out "$TMP_PRIV" 2>/dev/null
  openssl rsa -in "$TMP_PRIV" -pubout -out "$TMP_PUB" 2>/dev/null
  cp "$TMP_PRIV" "$AUTH_RES/private.pem"
  cp "$TMP_PUB"  "$AUTH_RES/public.pem"
  cp "$TMP_PUB"  "$GW_RES/public.pem"
else
  echo "🔐 Giữ nguyên JWT keypair local hợp lệ (đặt ROTATE_JWT_KEYS=true để rotate)."
fi

# Tạo service credential local một lần, không ghi đè secret đã có.
if [ ! -f "$ENV_FILE" ]; then
  umask 077
  : > "$ENV_FILE"
fi
if ! grep -q '^INTERNAL_SECRET=' "$ENV_FILE"; then
  printf 'INTERNAL_SECRET=%s\n' "$(openssl rand -hex 32)" >> "$ENV_FILE"
fi
if ! grep -q '^POSTGRES_PASSWORD=' "$ENV_FILE"; then
  printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> "$ENV_FILE"
fi

echo "✅ Đã ghi:"
echo "   - $AUTH_RES/private.pem  (PKCS#8)"
echo "   - $AUTH_RES/public.pem   (X.509)"
echo "   - $GW_RES/public.pem     (X.509, cùng cặp)"
echo "   - $ENV_FILE              (INTERNAL_SECRET + POSTGRES_PASSWORD local, không ghi đè)"
echo
echo "⚠️  Khóa này chỉ nằm ở máy local (đã .gitignore). Đừng commit."
echo "    Rotate có chủ đích: ROTATE_JWT_KEYS=true bash scripts/gen-keys.sh"
echo "    Production: cung cấp khóa qua secret manager / mount file, đặt env"
echo "    JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH (xem SECURITY.md)."
