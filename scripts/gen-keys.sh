#!/usr/bin/env bash
# =============================================================================
# Sinh cặp khóa RSA cho JWT (ký ở auth-service, verify ở api-gateway) trong
# thư mục secret operator-owned, không phải source tree/JAR.
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
SECRETS_DIR=".secrets"
PRIVATE_KEY_FILE="${SECRETS_DIR}/jwt-private.pem"
PUBLIC_KEY_FILE="${SECRETS_DIR}/jwt-public.pem"
INTERNAL_SECRET_FILE="${SECRETS_DIR}/internal-secret"
DB_PASSWORD_FILE="${SECRETS_DIR}/db-password"
ENV_FILE=".env"
ROTATE_JWT_KEYS="${ROTATE_JWT_KEYS:-false}"

TMP_PRIV="$(mktemp)"; TMP_PUB="$(mktemp)"
trap 'rm -f "$TMP_PRIV" "$TMP_PUB"' EXIT

# Auth cần private + public; Gateway chỉ nhận public qua Docker secret.
umask 077
mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"
keys_are_valid=false
if [[ -f "$PRIVATE_KEY_FILE" && -f "$PUBLIC_KEY_FILE" ]] \
    && openssl pkey -in "$PRIVATE_KEY_FILE" -pubout -outform PEM 2>/dev/null \
      | cmp -s - "$PUBLIC_KEY_FILE"; then
  keys_are_valid=true
fi

if [[ "$ROTATE_JWT_KEYS" == "true" || "$keys_are_valid" != "true" ]]; then
  echo "🔑 Sinh khóa RSA $BITS-bit ..."
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:"$BITS" -out "$TMP_PRIV" 2>/dev/null
  openssl rsa -in "$TMP_PRIV" -pubout -out "$TMP_PUB" 2>/dev/null
  cp "$TMP_PRIV" "$PRIVATE_KEY_FILE"
  cp "$TMP_PUB"  "$PUBLIC_KEY_FILE"
  chmod 600 "$PRIVATE_KEY_FILE" "$PUBLIC_KEY_FILE"
else
  echo "🔐 Giữ nguyên JWT keypair local hợp lệ (đặt ROTATE_JWT_KEYS=true để rotate)."
fi

# Tạo local secret files một lần, không ghi đè secret đã có.
if [ ! -f "$ENV_FILE" ]; then
  umask 077
  : > "$ENV_FILE"
fi
if [[ ! -s "$INTERNAL_SECRET_FILE" ]]; then
  legacy_secret="$(sed -n 's/^INTERNAL_SECRET=//p' "$ENV_FILE" | tail -n 1)"
  printf '%s\n' "${legacy_secret:-$(openssl rand -hex 32)}" > "$INTERNAL_SECRET_FILE"
  chmod 600 "$INTERNAL_SECRET_FILE"
fi
if [[ ! -s "$DB_PASSWORD_FILE" ]]; then
  legacy_db_password="$(sed -n 's/^POSTGRES_PASSWORD=//p' "$ENV_FILE" | tail -n 1)"
  printf '%s\n' "${legacy_db_password:-$(openssl rand -hex 24)}" > "$DB_PASSWORD_FILE"
  chmod 600 "$DB_PASSWORD_FILE"
fi
if ! grep -q '^GRAFANA_ADMIN_PASSWORD=' "$ENV_FILE"; then
  printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> "$ENV_FILE"
fi
if ! grep -q '^JWT_PRIVATE_KEY_FILE=' "$ENV_FILE"; then
  printf 'JWT_PRIVATE_KEY_FILE=%s\n' "$PRIVATE_KEY_FILE" >> "$ENV_FILE"
fi
if ! grep -q '^JWT_PUBLIC_KEY_FILE=' "$ENV_FILE"; then
  printf 'JWT_PUBLIC_KEY_FILE=%s\n' "$PUBLIC_KEY_FILE" >> "$ENV_FILE"
fi
if ! grep -q '^INTERNAL_SECRET_FILE=' "$ENV_FILE"; then
  printf 'INTERNAL_SECRET_FILE=%s\n' "$INTERNAL_SECRET_FILE" >> "$ENV_FILE"
fi
if ! grep -q '^DB_PASSWORD_FILE=' "$ENV_FILE"; then
  printf 'DB_PASSWORD_FILE=%s\n' "$DB_PASSWORD_FILE" >> "$ENV_FILE"
fi

echo "✅ Đã ghi:"
echo "   - $PRIVATE_KEY_FILE       (PKCS#8, private; Docker secret only)"
echo "   - $PUBLIC_KEY_FILE        (X.509 public; Docker secret only)"
echo "   - $INTERNAL_SECRET_FILE   (service credential; Docker secret only)"
echo "   - $DB_PASSWORD_FILE       (database password; Docker secret only)"
echo "   - $ENV_FILE               (local paths and generated placeholders, không ghi đè)"
echo
echo "⚠️  Khóa này chỉ nằm ở máy local (đã .gitignore). Đừng commit."
echo "    Rotate có chủ đích: ROTATE_JWT_KEYS=true bash scripts/gen-keys.sh"
echo "    Compose đọc JWT_*_KEY_FILE và mount chúng thành Docker secrets."
echo "    Production: inject key files from the approved secret manager, không copy vào image."
