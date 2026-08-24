# 005 — Bảo mật khóa JWT & secret (rotate + externalize + untrack)

> Ngày: 2026-07-22 · Service: auth-service, api-gateway
> Liên quan: [SECURITY.md](../../../SECURITY.md), [priority-roadmap](../plans/active/priority-roadmap.md)

## Mình đã làm gì
Đóng lỗ hổng **private key JWT bị commit vào git** (khóa ký RSA — ai có repo là giả
mạo được mọi token, kể cả ADMIN). Đã: rotate khóa, externalize đường dẫn khóa qua env,
gỡ khóa khỏi git tracking + gitignore, và làm internal secret fail-closed.
File: `TokenService.java`, `JwtPublicKeyProvider.java`, `AuthController.java`,
`application.properties`, `.gitignore`, thêm `scripts/gen-keys.sh`, `SECURITY.md`.

## Kỹ thuật quan trọng

### 1. Vì sao "secret trong repo" là hỏng, kể cả private repo
Repo bị clone nhiều nơi (CI, máy dev, fork). Một khi private key vào git, coi như đã
lộ vĩnh viễn — **và còn nằm mãi trong history** dù sau này xóa file. Với khóa ký JWT,
lộ = giả mạo được danh tính bất kỳ. Đây là rủi ro nặng hơn nhiều so với "hardcode secret".

### 2. Rotate ≠ chỉ xóa file
"Rotate" nghĩa là **sinh khóa mới** và cho hệ thống dùng khóa mới; khóa cũ coi như chết.
Xóa file khỏi HEAD chưa đủ vì history vẫn còn khóa cũ. Mình sinh cặp mới (không commit) →
production dùng cặp mới → cặp cũ vô dụng. Phần scrub history là thao tác viết lại lịch sử
(ảnh hưởng mọi clone) nên để chủ dự án tự quyết, mình chỉ tài liệu hóa.

### 3. Externalize: env-path trước, classpath fallback
Loader đọc `System.getenv("JWT_PRIVATE_KEY_PATH")` trước; nếu chưa set thì fallback
classpath. Nhờ vậy: **dev** chạy được không cần cấu hình (dùng khóa local), **prod**
mount khóa thật từ secret manager mà không cần đổi code, không bake khóa vào image.
Giữ format PEM parsing dùng chung một helper.

### 4. Fail-closed thay vì fail-open
Internal secret trước đây có default `GATEWAY_INTERNAL_SECRET_ABC123` — chưa cấu hình
là dùng secret ai-cũng-biết (fail-open). Sửa: default rỗng + check "blank → từ chối".
Nguyên tắc: **khi thiếu cấu hình bảo mật, hệ thống phải KHÓA lại, không mở ra.**

### 5. `git rm --cached` — gỡ track nhưng giữ file local
`git rm --cached <file>` xóa khỏi index (thôi track) nhưng **giữ file trên đĩa**, nên
dev vẫn build/chạy được, còn git thì không phân phối khóa nữa. Kết hợp với `.gitignore`
để không vô tình add lại.

## Quyết định & đánh đổi
- **Rotate ngay** (chưa production): đúng thời điểm, rủi ro thấp, token cũ chưa quan trọng.
- **Đánh đổi onboarding**: fresh clone giờ phải chạy `gen-keys.sh` trước khi build
  (khóa không còn trong repo). Đã ghi vào SECURITY.md + hướng dẫn. Đây là cái giá đúng
  cho việc không để secret trong repo.
- **Không tự scrub history**: viết lại lịch sử là thao tác phá vỡ mọi clone → cần con
  người quyết định, không làm ngầm.

## Cạm bẫy / lỗi dễ mắc
- Sinh khóa sai format (PKCS#1 vs PKCS#8) → service không load được. `openssl genpkey`
  cho PKCS#8, `rsa -pubout` cho X.509 — khớp đúng loader. Đã verify header + cặp khớp.
- Quên copy public key sang gateway → gateway verify fail toàn bộ token. Script copy cả 3 file.
- Untrack mà quên gitignore → lần `git add .` sau lại thêm khóa vào.

## Cách kiểm chứng
- `mvn -o compile` auth-service + api-gateway → **BUILD SUCCESS**.
- `git ls-files | grep .pem` → rỗng (không còn track); file local vẫn còn.
- Khóa mới: private = `BEGIN PRIVATE KEY` (PKCS#8), public = `BEGIN PUBLIC KEY` (X.509),
  public của auth ≡ gateway (diff MATCH).
- Round-trip ký/verify thật cần chạy cụm (chưa làm) — nhưng cặp khớp nên đảm bảo tương thích.

## Câu hỏi mở
- Bao giờ scrub git history (cần rewrite, ảnh hưởng mọi clone)?
- Production nạp khóa qua GCP Secret Manager hay mount file trực tiếp trên VM?

## Muốn đào sâu thêm
Từ khoá: "git rm --cached vs git rm", "git filter-repo remove secret", "RSA PKCS8
X509 openssl genpkey", "fail-closed vs fail-open security", "externalize secrets 12-factor".
