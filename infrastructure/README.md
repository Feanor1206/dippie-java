# Triển khai và vận hành Dippie bằng Docker

Thư mục `infrastructure/` chứa bộ cấu hình Docker cho Dippie để bạn có thể chạy trên máy cá nhân bằng một lệnh hoặc đưa thẳng lên VPS mà không cần sửa mã nguồn.

## 1. Kiến trúc triển khai

```text
                        Internet / Trình duyệt người dùng
                                       │
                        HTTP :80 / HTTPS :443 (+ HTTP/3 UDP)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Container: caddy (caddy:2-alpine) — DUY NHẤT mở cổng 80/443 ra ngoài   │
│  • Tự động cấp & gia hạn chứng chỉ HTTPS khi DOMAIN được đặt            │
│  • HTTP thuần (localhost) khi DOMAIN để trống                           │
│  • Security headers (HSTS trên HTTPS, CSP cho YouTube/SSE, nosniff...)  │
│  • Nén zstd/gzip, tắt đệm cho SSE /api/chat, giới hạn kích thước body   │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ Mạng Docker: edge
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Container: app (JRE 21 Alpine, user non-root 'dippie', cổng nội bộ 3000)│
│  • Spring Boot 3.5 (profile: prod) + giao diện React 19/Vite (/app/web) │
│  • Graceful shutdown (30s), nén phản hồi, HikariCP tối ưu cho VPS nhỏ   │
│  • Kiểm tra sức khỏe qua GET /api/health (xác thực cả kết nối MySQL)    │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ Mạng Docker: internal (internal: true)
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Container: mysql (mysql:8.4, KHÔNG mở cổng ra ngoài)                   │
│  • Volume bền vững: mysqldata (/var/lib/mysql)                          │
│  • Lần khởi tạo đầu tiên tự nạp theo thứ tự:                            │
│      01_init.sql -> database/migrations/*.sql -> database/objects/*.sql │
└─────────────────────────────────────────────────────────────────────────┘
```

### Danh sách tệp trong `infrastructure/`

| Tệp | Vai trò |
| --- | --- |
| `Dockerfile` | Build nhiều giai đoạn: giao diện React 19/Vite trên Node 22, backend Spring Boot trên JDK 21 dùng lại cache Maven, rồi đóng gói sang JRE 21 Alpine chạy bằng user không phải root có `HEALTHCHECK` |
| `docker-compose.yml` | Chạy production gồm `caddy`, `app` (`SPRING_PROFILES_ACTIVE=prod`) và `mysql:8.4` |
| `docker-compose.demo.yml` | Chế độ demo cục bộ độc lập dùng H2 và dữ liệu mẫu (chỉ để chạy thử, không dùng chạy thật) |
| `Caddyfile` | Cấu hình reverse proxy Caddy 2, cấp HTTPS tự động, thêm header bảo mật, CSP, SSE và giới hạn body |
| `.env.example` | Danh sách biến môi trường kèm giải thích và giá trị giữ chỗ |
| `init_migrations.sh` | Chạy lần lượt `database/migrations/*.sql` khi khởi tạo volume MySQL mới |
| `init_database_objects.sh` | Nạp view, function, procedure, trigger và tạo các tài khoản MySQL (`dippie_app`, `dippie_readonly`, `dippie_admin`) |

## 2. Yêu cầu trước khi chạy

- Máy cá nhân hoặc VPS đã cài Docker Engine 24+ và Docker Compose v2 (`docker compose version`).
- Cấu hình tối thiểu trên VPS nên có 2 vCPU, 2 GB RAM và 20 GB ổ đĩa trống (mức giới hạn mặc định: MySQL 768 MB, App 768 MB, Caddy 128 MB).
- Nếu triển khai trên VPS có tên miền:
  - Tạo bản ghi DNS `A` (và `AAAA` nếu dùng IPv6) trỏ tên miền về IP công khai của VPS.
  - Mở tường lửa máy chủ (ví dụ `ufw` hoặc Security Group của nhà cung cấp cloud) cho cổng TCP `80`, TCP `443` và UDP `443`. Không mở cổng `3000` hay `3306`.
- Có `openssl` (Linux, macOS, Git Bash) hoặc PowerShell `.NET` để tạo khóa ngẫu nhiên.

## 3. Triển khai lần đầu từng bước

### 3.1. Chuẩn bị tệp môi trường `infrastructure/.env`

Sao chép từ tệp mẫu:

```bash
cd infrastructure
cp .env.example .env
chmod 600 .env
```

Trên Windows PowerShell:

```powershell
Set-Location infrastructure
Copy-Item .env.example .env
```

Sinh khóa `APP_JWT_SECRET` (chuỗi Base64 từ 32 byte trở lên, ví dụ 48 byte ngẫu nhiên) và các mật khẩu MySQL:

```bash
# Tạo APP_JWT_SECRET (Linux / macOS / Git Bash)
openssl rand -base64 48

# Tạo mật khẩu ngẫu nhiên cho MySQL (chạy 4 lần cho 4 tài khoản)
openssl rand -base64 24
```

Trên Windows PowerShell (không cần `openssl`):

```powershell
# Tạo APP_JWT_SECRET (48 byte Base64)
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Mở `infrastructure/.env` rồi điền các mục sau:

1. `APP_JWT_SECRET`: dán chuỗi Base64 vừa tạo.
2. `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DIPPIE_DB_READONLY_PASSWORD`, `DIPPIE_DB_ADMIN_PASSWORD`: đặt 4 mật khẩu mạnh và khác nhau.
3. Tài khoản quản trị ban đầu (tùy chọn, nên đặt ở lần chạy đầu): điền đủ 3 biến `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_PASSWORD` (mật khẩu dài từ 16 đến 72 byte UTF-8).
4. Trợ lý CineBot (tùy chọn): để `DIPPIE_LLM_PROVIDER=template` nếu chạy không cần khóa API, hoặc đổi sang `deepseek` rồi điền `DEEPSEEK_API_KEY`.

Không commit tệp `.env` lên Git (`.gitignore` đã bỏ qua `.env` và `.env.*`).

### 3.2. Chạy cục bộ (`localhost`, HTTP thuần)

1. Trong `infrastructure/.env`, để trống `DOMAIN=`. Nếu cổng `80` trên máy đang bị chương trình khác chiếm, đổi `HTTP_PORT=8080` và `HTTPS_PORT=8443`.
2. Khởi chạy từ thư mục `infrastructure/`:

```bash
docker compose up -d --build
```

3. Chờ khoảng 30 đến 60 giây để MySQL khởi tạo lần đầu, rồi kiểm tra trạng thái:

```bash
docker compose ps
```

4. Mở trình duyệt tại `http://localhost` (hoặc `http://localhost:8080` nếu bạn đổi `HTTP_PORT`).
5. Sau khi đăng nhập tài khoản quản trị lần đầu thành công, xóa trắng `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_EMAIL` và `APP_BOOTSTRAP_ADMIN_PASSWORD` trong `.env`, rồi chạy lại `docker compose up -d app`.

### 3.3. Chạy trên VPS có tên miền (HTTPS tự động)

1. Trỏ bản ghi DNS `A` của tên miền (ví dụ `dippie.example.com`) về IP VPS và kiểm tra bằng `dig +short dippie.example.com`.
2. Mở tường lửa cho `80/tcp`, `443/tcp`, `443/udp`:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
```

3. Trong `infrastructure/.env`:
   - Đặt `DOMAIN=dippie.example.com` (không ghi `https://`).
   - Giữ nguyên `HTTP_PORT=80` và `HTTPS_PORT=443`.
   - Để trống `CORS_ALLOWED_ORIGINS=` vì giao diện và API chạy cùng nguồn qua Caddy.
4. Khởi chạy:

```bash
cd infrastructure
docker compose up -d --build
```

5. Caddy sẽ tự xin chứng chỉ TLS (Let's Encrypt / ZeroSSL), chuyển toàn bộ HTTP sang HTTPS và bật header `Strict-Transport-Security` (HSTS).
6. Mở `https://dippie.example.com` để kiểm tra chứng chỉ TLS.

### 3.4. Chạy nhanh chế độ demo cục bộ (H2, không cần MySQL)

Dùng khi muốn chạy thử nhanh trên máy cá nhân mà không cần tạo `.env` hay cài MySQL:

```bash
cd infrastructure
docker compose -f docker-compose.demo.yml up -d --build
```

Truy cập `http://localhost:3000` (tài khoản mẫu là `admin` hoặc `user`, mật khẩu `password123`). Dừng lại bằng:

```bash
docker compose -f docker-compose.demo.yml down
```

Không dùng `docker-compose.demo.yml` trên máy chủ công khai.

## 4. Cập nhật phiên bản mới

Nhớ sao lưu cơ sở dữ liệu trước khi kéo code mới:

```bash
# 1. Sao lưu MySQL trước khi cập nhật
./scripts/backup-mysql.sh

# 2. Kéo mã nguồn mới
git pull --ff-only

# 3. Kiểm tra xem có tệp mới trong database/migrations/ hay không
#    (Docker chỉ chạy /docker-entrypoint-initdb.d khi volume MySQL còn TRỐNG.
#     Với volume đã tồn tại, áp dụng thủ công các migration mới theo thứ tự số!)
ls -la database/migrations/

# 4. Build lại và khởi động lại ứng dụng (MySQL và Caddy giữ nguyên volume)
cd infrastructure
docker compose up -d --build app

# 5. Nếu có thay đổi Caddyfile, tải lại cấu hình Caddy không ngắt kết nối
docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile

# 6. Kiểm tra sức khỏe sau cập nhật
docker compose ps
docker compose logs --tail=50 app
```

Nếu có migration SQL mới trên hệ thống đang chạy (ví dụ `14_example.sql`), nạp thẳng qua `docker compose exec`:

```bash
docker compose -f infrastructure/docker-compose.yml --env-file infrastructure/.env \
  exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
  < database/migrations/14_example.sql
```

## 5. Sao lưu và khôi phục MySQL

Các script trong `scripts/` chạy `mysqldump` qua `docker compose exec -T mysql` (không lộ mật khẩu trên danh sách tiến trình), nén thành `.sql.gz`, kiểm tra tệp gzip, in mã SHA-256 và tự xóa các bản cũ quá số `N` bản gần nhất (mặc định giữ 14 bản).

### Linux / macOS (Bash)

```bash
# Sao lưu vào ./backups và giữ 14 bản gần nhất
./scripts/backup-mysql.sh

# Tùy chỉnh thư mục đích và số bản giữ lại (ví dụ 30 bản)
./scripts/backup-mysql.sh /var/backups/dippie 30

# Khôi phục (có yêu cầu gõ YES để xác nhận)
./scripts/restore-mysql.sh ./backups/dippie-mysql-20261125-120000.sql.gz
```

Đặt lịch sao lưu tự động hằng ngày lúc 02:30 UTC trên VPS bằng `crontab -e`:

```cron
30 2 * * * /opt/dippie/scripts/backup-mysql.sh /var/backups/dippie 14 >> /var/log/dippie-backup.log 2>&1
```

### Windows (PowerShell)

```powershell
# Sao lưu vào .\backups và giữ 14 bản gần nhất
.\scripts\backup-mysql.ps1

# Tùy chỉnh thư mục đích và số bản giữ lại
.\scripts\backup-mysql.ps1 -BackupDir D:\Dippie-backups -Keep 30

# Khôi phục (có yêu cầu gõ YES để xác nhận)
.\scripts\restore-mysql.ps1 -BackupFile .\backups\dippie-mysql-20261125-120000.sql.gz
```

Nên chép định kỳ tệp `.sql.gz` sang nơi lưu trữ ngoài VPS (S3, Backblaze B2, Google Drive hoặc máy chủ dự phòng) và thử khôi phục mỗi tháng một lần.

## 6. Xoay vòng khóa bí mật JWT (`APP_JWT_SECRET`)

`JwtService` dùng một khóa đối xứng HS256 duy nhất lấy từ `APP_JWT_SECRET`. Khi đổi khóa mới, toàn bộ token đăng nhập và playback grant cũ sẽ mất hiệu lực ngay và người dùng phải đăng nhập lại.

Các bước thay khóa:

1. Báo trước khung giờ bảo trì ngắn nếu đang có người xem.
2. Tạo khóa Base64 mới tối thiểu 32 byte:
   ```bash
   openssl rand -base64 48
   ```
3. Thay `APP_JWT_SECRET` mới vào `infrastructure/.env`.
4. Tạo lại container `app` để nhận biến môi trường mới:
   ```bash
   cd infrastructure
   docker compose up -d --force-recreate app
   ```
5. Kiểm tra log khởi động và gọi `/api/health` xem ứng dụng đã lên `healthy` chưa. Nếu khóa sai định dạng Base64 hoặc ngắn hơn 32 byte, `app` sẽ từ chối chạy ngay kèm thông báo lỗi.

## 7. Xem nhật ký (logs)

Mọi container dùng driver `json-file` có xoay vòng tự động (`max-size: 10m`, `max-file: 5`, tối đa khoảng 50 MB mỗi dịch vụ) để không làm đầy ổ đĩa VPS.

```bash
cd infrastructure

# Xem log trực tiếp của tất cả dịch vụ
docker compose logs -f --tail=100

# Chỉ xem log backend Spring Boot
docker compose logs -f --tail=100 app

# Chỉ xem log reverse proxy Caddy (chứng chỉ HTTPS, mã phản hồi)
docker compose logs -f --tail=100 caddy

# Chỉ xem log MySQL (khởi tạo schema/migration, cảnh báo kết nối)
docker compose logs -f --tail=100 mysql
```

## 8. Kiểm tra sức khỏe dịch vụ (healthcheck)

Mỗi dịch vụ trong `docker-compose.yml` có một `healthcheck` riêng:

- `mysql`: chạy `mysqladmin ping` mỗi 10 giây; `app` đợi `mysql` đạt trạng thái `healthy` mới khởi động.
- `app`: gọi nội bộ `GET http://127.0.0.1:3000/api/health` mỗi 15 giây (endpoint này kiểm tra kết nối thật tới MySQL với timeout 2 giây, trả `200 UP` hoặc `503 DOWN`). `caddy` đợi `app` đạt `healthy` mới chạy.
- `caddy`: gọi admin API nội bộ `http://127.0.0.1:2019/config/` mỗi 30 giây.

Kiểm tra nhanh từ dòng lệnh:

```bash
# Kiểm tra trạng thái container và healthcheck
cd infrastructure
docker compose ps

# Kiểm tra qua Caddy (cục bộ)
curl -fsS http://localhost/api/health

# Kiểm tra qua Caddy (VPS có tên miền)
curl -fsS https://dippie.example.com/api/health
```

Khi hoạt động bình thường, lệnh trả mã HTTP `200` và thân JSON chứa `"status":"UP"`.

## 9. Danh sách kiểm tra trước khi đưa lên chạy thật

- [ ] Đã tạo `infrastructure/.env` từ `.env.example`, đặt quyền `chmod 600 .env`, và xác nhận `git status` không theo dõi `.env`.
- [ ] `APP_JWT_SECRET` là chuỗi Base64 ngẫu nhiên tối thiểu 32 byte, không dùng khóa mẫu cũ.
- [ ] Cả 4 mật khẩu MySQL (`MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DIPPIE_DB_READONLY_PASSWORD`, `DIPPIE_DB_ADMIN_PASSWORD`) đều mạnh và khác nhau.
- [ ] `SPRING_PROFILES_ACTIVE=prod` đang bật (xem trong log `app`: không bật `demo`, không bật H2 console, không mở Swagger UI `/swagger-ui.html`, `ddl-auto: validate`).
- [ ] Đã tạo tài khoản quản trị đầu tiên bằng `APP_BOOTSTRAP_ADMIN_*`, đăng nhập được, rồi xóa trắng 3 biến này khỏi `.env` và khởi động lại `app`.
- [ ] Trên VPS: đã điền `DOMAIN=<tên-miền>`, DNS trỏ đúng IP, tường lửa chỉ mở `80/tcp`, `443/tcp`, `443/udp` (không mở `3000` hay `3306`).
- [ ] Mở `https://<tên-miền>` thấy chứng chỉ TLS hợp lệ, phản hồi có `Strict-Transport-Security`, `X-Content-Type-Options`, `Content-Security-Policy`, phát được video nhúng YouTube, tải được ảnh thu nhỏ `i.ytimg.com` và chat SSE `/api/chat`.
- [ ] Đã chạy thử `./scripts/backup-mysql.sh` (hoặc `backup-mysql.ps1`), kiểm tra tệp `.sql.gz` hợp lệ và đặt lịch sao lưu định kỳ ra ngoài máy chủ.
- [ ] `docker compose ps` hiện cả 3 dịch vụ (`caddy`, `app`, `mysql`) ở trạng thái `Up (healthy)` và `GET /api/health` trả HTTP `200`.
