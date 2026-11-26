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
- Hồ sơ trẻ em tùy chọn bật trong trang Cài đặt tài khoản: đặt mã PIN phụ huynh (4–6 số, khóa tạm 5 phút sau 5 lần nhập sai), tạo tối đa 6 hồ sơ riêng tách biệt lịch sử xem/thư viện/đánh giá, chuyển hồ sơ nhanh và chế độ trẻ em ẩn menu tài khoản, quản trị cùng các liên kết mở ra YouTube bên ngoài.
- Giới hạn thời gian xem mỗi ngày và giải toán thưởng phút cho riêng từng hồ sơ trẻ em (đặt lại vào 0h00 giờ `Asia/Ho_Chi_Minh`): phụ huynh chọn hạn mức ngày ngay khi tạo hồ sơ hoặc chỉnh nhanh trên thẻ hồ sơ bé (mức nhanh `30 / 60 / 90 / 120 phút`, tùy chỉnh `0–600 phút`, hoặc `Không giới hạn`), cấu hình cấp độ toán (4 mức), phút thưởng mỗi câu đúng, trần thưởng ngày và tặng nhanh thêm phút bằng mã PIN; khi hết giờ, máy chủ khóa phát video và lưu tiến độ bằng mã `423`, còn giao diện trẻ em tạm dừng trình phát và mở màn hình giải toán để nhận thêm thời gian (hồ sơ không giới hạn, hồ sơ chính và tài khoản không bật chế độ phụ huynh không bao giờ bị giới hạn).
- Bộ tính năng phụ huynh cho hồ sơ trẻ em trong trang Cài đặt tài khoản và giao diện trẻ em:
  - **Bảng nhiệm vụ đời thực**: phụ huynh tạo nhiệm vụ (1–120 phút thưởng, lặp mỗi ngày hoặc làm một lần, chọn từng bé nhìn thấy); bé bấm "Nộp nhiệm vụ" (tối đa 1 lượt chờ duyệt mỗi nhiệm vụ/bé) và ba mẹ duyệt/từ chối bằng mã PIN từ hồ sơ chính hoặc ngay trên máy ở chế độ trẻ em để cộng mục `BOUNTY` vào sổ cái hôm nay.
  - **Két sắt phim cuối tuần**: khóa phim lẻ từ thứ Hai đến thứ Sáu theo giờ `Asia/Ho_Chi_Minh`; thanh tiến độ "Vé xem phim" tự tăng `+20%` mỗi ngày bé dừng xem trước khi hết giờ và `+20%` mỗi ngày giải đủ 3 câu thưởng toán (tối đa `100%`), mở khóa tự động vào thứ Bảy & Chủ Nhật khi đạt `100%` hoặc khi ba mẹ mở thủ công bằng mã PIN.
  - **Phim VIP theo độ khó**: phụ huynh đánh dấu phim bộ VIP riêng cho từng bé; mỗi tập VIP yêu cầu bé giải đúng 1 bài toán ở mức `min(4, mức toán của bé + 1)` để mở khóa tập đó trong hôm nay.
- Tính năng tương tác trên trình phát dành riêng cho chế độ trẻ em (phụ huynh bật/tắt và chỉnh ngưỡng cho từng bé bằng mã PIN): Dieppie canh giờ nghỉ mắt theo thời gian xem liên tục (vui vẻ → đổ mồ hôi → chóng mặt → ngất xỉu, chọn cho Dieppie ngủ khóa cứng phía máy chủ hoặc giải toán mua thuốc hồi sức cho Dieppie), Rương báu bay ngang sau mỗi 5–7 phút xem (giải toán nhanh 10 giây cộng +3 phút, combo 3 tặng thêm +1 phút vào sổ cái `LOOT` theo trần thưởng ngày) và Xếp lại cốt truyện để qua tập (câu hỏi ôn tập dựng từ cảnh/phân đoạn/nhân vật/tên tập có sẵn để mở khóa tập kế tiếp).
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
