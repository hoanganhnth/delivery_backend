# 🔐 Security — Khóa JWT & Secret

> Cập nhật: 2026-07-28

## Khóa JWT (RSA)

auth-service **ký** JWT bằng private key; api-gateway **verify** bằng public key.
Format code yêu cầu:
- `private.pem` — PKCS#8 (`BEGIN PRIVATE KEY`)
- `public.pem` — X.509 (`BEGIN PUBLIC KEY`)

### Khóa KHÔNG nằm trong git
Các file `*.pem` đã được `.gitignore` và gỡ khỏi git tracking. **Không commit khóa.**

### Lần đầu clone / trước khi build
Sinh khóa local (auth cần private+public, gateway cần public — cùng cặp):
```bash
bash scripts/gen-keys.sh
```
Script giữ nguyên keypair hợp lệ hiện có; chỉ rotate có chủ đích bằng
`ROTATE_JWT_KEYS=true bash scripts/gen-keys.sh` để tránh vô tình vô hiệu hóa token
khi chỉ cần bổ sung secret còn thiếu trong `.env`.
> Nếu build (kể cả `docker compose build`) mà thiếu khóa → auth-service sẽ báo lỗi
> rõ ràng khi khởi động. Chạy `gen-keys.sh` rồi build lại.

### Nạp khóa khi deploy (production)
Loader bắt buộc nhận vị trí khóa qua env hoặc Spring property; cấu hình mặc định
để trống để service fail-fast khi chưa mount secret:
- auth-service: `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`
- api-gateway: `JWT_PUBLIC_KEY_PATH`

Các env trên được bridge qua Spring properties `jwt.private-key.path` và
`jwt.public-key.path`; có thể dùng filesystem path, `file:` URI hoặc
`classpath:` resource nếu được cấu hình rõ ràng. Không còn implicit classpath
fallback tới PEM trong main resources. Auth fail-fast khi thiếu/sai format hoặc
private/public không cùng cặp; Gateway fail-fast khi public key không đọc/parse
được.

Mount khóa từ secret manager (GCP Secret Manager / K8s Secret) vào một path rồi trỏ env,
thay vì bake vào image.

## ⚠️ Việc bắt buộc làm thủ công (chưa xử lý tự động)

1. **Scrub git history.** Khóa private cũ **vẫn còn trong lịch sử git** (từng bị commit).
   Rotate (đã làm — khóa active hiện tại là khóa mới, không nằm trong git) chỉ vô hiệu
   hóa khóa cũ NẾU production dùng khóa mới. Nhưng để xóa hẳn khóa cũ khỏi history, cần:
   ```bash
   # ví dụ dùng git filter-repo
   git filter-repo --path auth-service/src/main/resources/private.pem --invert-paths
   ```
   Đây là thao tác viết lại history (ảnh hưởng mọi clone) — chủ dự án tự quyết định khi nào chạy.

2. **Coi khóa cũ là đã lộ.** Bất kỳ token nào ký bằng khóa cũ không còn hợp lệ sau rotate.

## Internal secret (Auth ↔ User)

- Auth→User registration/linkage và block-state projection dùng header
  `Internal-Token`; account lookup theo email không có consumer đã bị loại bỏ.
- Secret lấy từ env `INTERNAL_SECRET`. **Không còn default trong code**.
  Đặt cùng `INTERNAL_SECRET`
  cho auth-service và user-service; `scripts/gen-keys.sh` sinh giá trị local một
  lần trong `.env`, production lấy từ secret manager.

## PostgreSQL password

- Main application config không còn fallback `123456`; chạy ngoài Compose phải
  đặt `DB_PASSWORD`.
- Compose bắt buộc `POSTGRES_PASSWORD` và truyền cùng giá trị cho đủ 13 JPA
  service. `scripts/gen-keys.sh` sinh password local ngẫu nhiên vào `.env` nếu
  chưa có; production lấy từ secret manager.
- PostgreSQL volume đã được tạo trước đây không tự đổi password khi env thay đổi.
  Hãy rotate role password có kiểm soát hoặc tạo lại local volume sau khi sao lưu;
  không xóa volume chỉ để vượt qua verifier.

## Provider credentials

- Firebase service-account JSON không nằm trong source/JAR. Notification service
  vẫn khởi động khi chưa cấu hình push; để bật FCM, mount file ngoài image và đặt
  `FIREBASE_SERVICE_ACCOUNT_KEY_PATH=file:/run/secrets/firebase.json`.
- Agora dùng `AGORA_APP_ID` và `AGORA_APP_CERTIFICATE`; không có default trong
  source. Livestream là capability phụ và không được public như MVP-ready khi hai
  biến này chưa được cấu hình và kiểm thử.
- Firebase JSON và Agora credential cũ từng được commit nên phải coi là đã lộ,
  revoke/rotate tại provider và scrub khỏi history trong cùng đợt xử lý history
  với JWT key.

## Checklist trước khi lên production
- [ ] `gen-keys.sh` cho local hoặc mount khóa thật qua env (không dùng khóa dev).
- [ ] Set `INTERNAL_SECRET` (chuỗi mạnh) nếu dùng endpoint nội bộ.
- [ ] Scrub `private.pem` khỏi git history.
- [ ] Revoke/rotate rồi scrub Firebase và Agora credential cũ khỏi git history.
- [x] Bỏ mật khẩu DB mặc định khỏi main config/Compose; bắt buộc env và verifier
  kiểm mọi JPA service dùng cùng credential.
