#!/usr/bin/env bash
set -euo pipefail

# =========================================================================
# Pre-MR Verification Script
# Chạy trước khi git push / tạo Merge Request để đảm bảo CI/CD không bị fail
# =========================================================================

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}✓ [PASS]${NC} $1"; }
fail() { echo -e "${RED}✗ [FAIL]${NC} $1"; exit 1; }
info() { echo -e "${BLUE}ℹ [INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}⚠ [WARN]${NC} $1"; }

echo -e "\n=============================================="
echo -e "   🚀 BẮT ĐẦU KIỂM TRA CHẤT LƯỢNG TRƯỚC KHI TẠO MR"
echo -e "==============================================\n"

# 1. Kiểm tra rò rỉ Key / Credentials nhạy cảm
info "1/5. Quét file nhạy cảm và Secret..."
if git status --porcelain | grep -E "(gcp-key\.json|\.pem|\.env\.local)"; then
  fail "Phát hiện file nhạy cảm (gcp-key.json, .pem, .env.local) chưa được bỏ qua! Không được commit."
fi
bash scripts/verify-secrets.sh
pass "Quét Secret an toàn."

# 2. Kiểm tra tính toàn vẹn của HTTP Contracts và API Inventory
info "2/5. Kiểm tra API Contracts và HTTP Inventory..."
./scripts/verify-http-api-inventory.sh
node docs/platform/system/api/generate-http-contract.mjs --check
pass "API Contracts đạt chuẩn."

# 3. Kiểm tra Kubernetes Manifests
info "3/5. Kiểm tra tính hợp lệ của Kubernetes Manifests..."
node deploy/kubernetes/generate.mjs --check
pass "Kubernetes Manifests khớp với generator."

# 4. Kiểm tra biên dịch Java và Unit Tests
info "4/5. Kiểm tra biên dịch Java (test-compile)..."
mvn -B test-compile -DskipTests
pass "Biên dịch mã nguồn Java thành công."

# 5. Chạy nhanh Unit Tests cho các module
info "5/5. Chạy Unit Tests xác thực..."
mvn -B test -Dtest="*Test" -DfailIfNoTests=false
pass "Toàn bộ Unit Tests đã vượt qua."

echo -e "\n=============================================="
echo -e "${GREEN}🎉 TẤT CẢ KIỂM TRA ĐÃ ĐẠT CHUẨN! BẠN CÓ THỂ PUSH VÀ TẠO MR.${NC}"
echo -e "==============================================\n"
