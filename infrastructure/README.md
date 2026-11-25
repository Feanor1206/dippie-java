# Docker

`docker-compose.yml` chạy ứng dụng Dippie (Spring Boot 3.5 / Java 21 cùng giao diện React đã build bởi `infrastructure/Dockerfile`) và MySQL 8.0. Ứng dụng phục vụ cả giao diện và API tại cổng `3000`.

Khi khởi tạo volume MySQL mới, container MySQL tự nạp `database/schema/01_init.sql` và toàn bộ tệp trong `database/objects/*.sql` qua `init_database_objects.sh`.

## Khởi chạy

Tạo tệp `.env` từ `.env.example` rồi điền mật khẩu MySQL (`MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DIPPIE_DB_READONLY_PASSWORD`, `DIPPIE_DB_ADMIN_PASSWORD`) và khóa bí mật `APP_JWT_SECRET`. Giữ cùng tên dự án Compose khi tái sử dụng volume MySQL.

```powershell
cd infrastructure
docker compose up -d --build
```

Mở [Dippie](http://localhost:3000/). Nếu chỉ muốn chạy MySQL trong Docker và chạy backend Java trực tiếp bằng `.\start-java.ps1 -Profile mysql`, dùng:

```powershell
cd infrastructure
docker compose up -d mysql
```
