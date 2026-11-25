# Dippie

Dippie là trang web xem phim hoạt hình gom backend Java (Spring Boot) và giao diện React vào chung một ứng dụng chạy ở cổng `3000`. Backend xử lý toàn bộ API nghiệp vụ, chạy trợ lý CineBot cục bộ và phục vụ trực tiếp bản build của React.

> Nếu muốn chạy thử nhanh mà không cần tự cài Java hay Node.js, bạn xem [HUONG_DAN_CHAY.md](HUONG_DAN_CHAY.md). Trên Windows chỉ cần giải nén rồi nhấp đúp `CHAY_DIPPIE.bat`, còn trên macOS hoặc Linux chạy `bash run-all.sh`.

## Công nghệ sử dụng

- Backend: Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA / Hibernate, OpenAPI (Swagger UI).
- Frontend: React 19, TypeScript, Vite, Tailwind CSS.
- Cơ sở dữ liệu: H2 cho chế độ `demo` cục bộ và MySQL 8.0 cho chế độ `mysql`.

## Tính năng chính

- Duyệt phim và bộ hoạt hình theo danh mục, bộ phim, mùa và tập; có lọc theo thể loại, năm phát hành và tìm kiếm toàn văn.
- Phát video qua HTTP Byte Range hoặc nguồn YouTube đã lưu, ghi tiến độ xem bằng khóa lạc quan và gợi ý tập xem tiếp theo.
- Lưu danh sách xem sau, phim yêu thích, chấm điểm từ 1 đến 10 và gợi ý phim theo sở thích hoặc tâm trạng.
- Trợ lý CineBot chạy trực tiếp trên Java qua SSE (`/api/chat`) dựa trên chỉ mục từ vựng BM25 trong bộ nhớ. Mặc định CineBot dựng câu trả lời tiếng Việt từ danh mục có sẵn; nếu muốn dùng DeepSeek, bạn đặt biến môi trường `DIPPIE_LLM_PROVIDER=deepseek` và `DEEPSEEK_API_KEY`.
- Khu vực quản trị để kiểm tra và nhập/xuất gói danh mục chuẩn `dippie.catalog.v1`, quản lý tài khoản, phân quyền, xem thống kê và nhật ký kiểm toán.

## Chạy nhanh (chế độ demo)

Cần Java 21 và Node.js 22.12 trở lên (Node.js chỉ cần khi bạn build hoặc sửa giao diện). Chạy lệnh sau từ thư mục gốc của kho mã:

```powershell
.\start-java.ps1
```

Script tự build `frontend/dist` nếu chưa có rồi khởi động Spring Boot ở chế độ `demo`. Cơ sở dữ liệu H2 lưu tại `target\dippie-java`, còn web mở ở `http://localhost:3000/`.

Hai tài khoản mẫu tạo sẵn trong chế độ `demo` dùng chung mật khẩu `password123`:

- Quản trị viên: `admin` (`admin@dippie.com`)
- Người dùng: `user` (`user@dippie.com`)

### Nhập danh mục phim mẫu

Khi web đã chạy, đăng nhập tài khoản `admin` rồi nhập 6 gói danh mục trong `data/catalog-by-series/` tại trang [Nhập dữ liệu](http://localhost:3000/admin/import), hoặc chạy vòng lặp PowerShell bên dưới để nhập một lượt:

```powershell
$password = ConvertTo-SecureString 'password123' -AsPlainText -Force
$catalogs = @('jadoo', 'larva', 'ori', 'doraemon', 'trang_quynh', 'peppa_pig')
foreach ($catalog in $catalogs) {
    .\scripts\import_preview_catalog.ps1 `
        -CatalogPath "data/catalog-by-series/catalog.$catalog.json" `
        -ApiBaseUrl 'http://127.0.0.1:3000/api' `
        -Username 'admin' `
        -Password $password
}
```

## Chế độ MySQL 8

Đặt các biến môi trường kết nối MySQL (`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`) cùng `APP_JWT_SECRET` (và `APP_BOOTSTRAP_ADMIN_*` nếu cần tạo tài khoản quản trị đầu tiên), rồi chạy:

```powershell
.\start-java.ps1 -Profile mysql
```

Nếu muốn dựng nhanh cả MySQL 8.0 và ứng dụng Java bằng Docker Compose, xem hướng dẫn ở [infrastructure/README.md](infrastructure/README.md).

## Ứng dụng quản trị desktop

Dippie Admin viết bằng JavaFX và JDBC. Từ thư mục gốc, chạy `.\start-desktop.ps1` khi máy đã có JDK 21 trở lên và bộ nhớ đệm Maven. Đăng nhập bằng một tài khoản quản trị đang hoạt động của website.

Mặc định app đọc tệp H2 tại `target/dippie-java/dippie.mv.db` trong kho mã hiện tại. Muốn chuyển sang MySQL, sao chép `desktop/desktop.properties.example` thành `desktop/desktop.properties` rồi điền URL JDBC, tên người dùng và mật khẩu của cơ sở dữ liệu website.

App cho phép xem danh mục, quản lý tài khoản và kiểm duyệt đánh giá; mỗi thay đổi đều ghi nhật ký trong cùng một giao dịch.

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

Backend (chạy trong `backend/springboot/`):

```powershell
.\mvnw.cmd -B test
```

Frontend (chạy trong `frontend/`):

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
