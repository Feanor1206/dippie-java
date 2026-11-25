# Dippie

Dippie là nền tảng web xem phim hoạt hình sử dụng kiến trúc hợp nhất: backend Java (Spring Boot) đảm nhiệm toàn bộ API nghiệp vụ, trợ lý CineBot cục bộ và phục vụ giao diện React đã build trên cùng cổng `3000`.

## Công nghệ

- **Backend**: Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA / Hibernate, OpenAPI (Swagger UI).
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS.
- **Cơ sở dữ liệu**: H2 (chế độ `demo` cục bộ) và MySQL 8.0 (chế độ `mysql`).

## Tính năng chính

- **Duyệt phim và bộ hoạt hình**: duyệt theo danh mục, bộ phim, mùa, tập, lọc theo thể loại/năm và tìm kiếm toàn văn.
- **Trình phát và tiến độ xem**: phát video HTTP Byte Range hoặc nguồn YouTube đã lưu, lưu tiến độ xem (khóa lạc quan) và gợi ý tập xem tiếp theo.
- **Thư viện cá nhân và gợi ý**: danh sách xem sau, yêu thích, đánh giá điểm 1–10 và gợi ý phim theo sở thích hoặc tâm trạng.
- **CineBot trên Java**: hỏi đáp SSE (`/api/chat`) dựa trên chỉ mục từ vựng BM25 cục bộ trong bộ nhớ; mặc định sinh câu trả lời mẫu tiếng Việt trực tiếp từ danh mục và hỗ trợ tùy chọn nhà cung cấp DeepSeek qua biến môi trường (`DIPPIE_LLM_PROVIDER=deepseek`, `DEEPSEEK_API_KEY`).
- **Quản trị**: kiểm tra hợp lệ và nhập/xuất gói danh mục chuẩn `dippie.catalog.v1`, quản lý người dùng và phân quyền, thống kê vận hành và nhật ký kiểm toán.

## Khởi chạy nhanh (chế độ Demo)

Yêu cầu Java 21 và Node.js 22.12 trở lên (Node.js chỉ cần khi build hoặc chỉnh sửa giao diện). Chạy từ thư mục gốc của kho mã:

```powershell
.\start-java.ps1
```

Script tự build `frontend/dist` nếu chưa có và khởi động Spring Boot ở chế độ `demo` (H2 lưu tại `target\dippie-java`, mở tại `http://localhost:3000/`).

Tài khoản mẫu có sẵn trong chế độ `demo` (mật khẩu chung `password123`):

- Quản trị viên: `admin` (`admin@dippie.com`)
- Người dùng: `user` (`user@dippie.com`)

### Nhập danh mục phim mẫu

Sau khi ứng dụng khởi động, đăng nhập tài khoản `admin` và nhập 8 gói danh mục trong `data/catalog-by-series/` tại trang [Nhập dữ liệu](http://localhost:3000/admin/import), hoặc nhập hàng loạt bằng PowerShell:

```powershell
$password = ConvertTo-SecureString 'password123' -AsPlainText -Force
$catalogs = @('jadoo', 'larva', 'ori', 'doraemon', 'trang_quynh', 'pokemon', 'mr_bean_animated', 'peppa_pig')
foreach ($catalog in $catalogs) {
    .\scripts\import_preview_catalog.ps1 `
        -CatalogPath "data/catalog-by-series/catalog.$catalog.json" `
        -ApiBaseUrl 'http://127.0.0.1:3000/api' `
        -Username 'admin' `
        -Password $password
}
```

## Chế độ MySQL 8

Cấu hình các biến môi trường kết nối MySQL (`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`) cùng `APP_JWT_SECRET` (và `APP_BOOTSTRAP_ADMIN_*` nếu cần tạo tài khoản quản trị ban đầu), sau đó chạy:

```powershell
.\start-java.ps1 -Profile mysql
```

Có thể dựng nhanh cả MySQL 8.0 và ứng dụng Java bằng Docker Compose trong [infrastructure/README.md](infrastructure/README.md).

## Cấu trúc kho mã

| Đường dẫn | Vai trò |
| --- | --- |
| `backend/springboot/` | Mã nguồn backend Spring Boot 3.5 (Java 21), API nghiệp vụ, CineBot và kiểm thử Maven |
| `frontend/` | Giao diện React + TypeScript + Vite và bộ kiểm thử giao diện |
| `database/` | Lược đồ MySQL 8.0 (`schema/`), migration (`migrations/`), view/routine/trigger (`objects/`), seed và truy vấn đo hiệu năng |
| `data/catalog-by-series/` | 8 gói danh mục hoạt hình đầy đủ theo chuẩn `dippie.catalog.v1` |
| `docs/` | Tài liệu thiết kế cơ sở dữ liệu (`docs/database/`) và JSON schema danh mục (`docs/contracts/animation-catalog-v1/`) |
| `infrastructure/` | `Dockerfile` đa tầng và `docker-compose.yml` chạy Spring Boot cùng MySQL 8.0 |
| `scripts/` | Script hỗ trợ nạp đối tượng MySQL (`load_database_objects.ps1`) và nhập danh mục qua API (`import_preview_catalog.ps1`) |
| `start-java.ps1` | Script khởi động nhanh trên Windows (`-Profile demo\|mysql`) |

## Kiểm thử

Backend (từ `backend/springboot/`):

```powershell
.\mvnw.cmd -B test
```

Frontend (từ `frontend/`):

```powershell
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

## Tài liệu liên quan

- [Kiến trúc và API backend Spring Boot](backend/springboot/README.md)
- [Thiết kế và vận hành cơ sở dữ liệu MySQL](database/README.md)
- [Phát triển giao diện React/Vite](frontend/README.md)
- [Danh mục phim và nguồn dữ liệu](data/README.md)
- [Triển khai với Docker](infrastructure/README.md)
