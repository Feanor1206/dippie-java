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
- Giới hạn thời gian xem mỗi ngày và ngân hàng câu hỏi trắc nghiệm 4 đáp án (A–D) theo khối lớp (`Mẫu giáo` đến `Lớp 5`) cho riêng từng hồ sơ trẻ em (đặt lại giờ xem vào 0h00 giờ `Asia/Ho_Chi_Minh`):
  - Phụ huynh chọn hạn mức ngày, khối lớp và bộ câu hỏi ngay khi tạo hồ sơ bé hoặc chỉnh trực tiếp trên thẻ hồ sơ bé (`Câu hỏi: Lớp 3 · Toán học, Tiếng Anh từ vựng, Ôn tập tuần 5`) kèm xem trước 2–3 câu mẫu; có mức nhanh `30 / 60 / 90 / 120 phút`, tùy chỉnh `0–600 phút`, hoặc `Không giới hạn`, và nút tặng thêm phút bằng mã PIN.
  - **Kho câu hỏi có sẵn & bộ câu hỏi riêng của phụ huynh**: gồm bộ Toán tự sinh 4 đáp án theo lớp (không ra số âm cho bé nhỏ; Lớp 3 dùng bảng nhân/chia 2–9), 30 câu Tiếng Anh từ vựng và 30 câu Đố vui / Khoa học trải đều các lớp. Từ trang Tài khoản, phụ huynh tự tạo tối đa 20 bộ riêng (tối đa 500 câu/bộ), thêm câu bằng biểu mẫu ngắn hoặc tải lên tệp CSV UTF-8 có BOM thân thiện với Excel (`cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop`, tối đa 1 MB, có tệp mẫu 3 dòng ví dụ và bảng xem trước báo lỗi từng dòng bằng tiếng Việt, chỉ nhập các dòng hợp lệ). Các bộ tự tải lên hoàn toàn riêng tư theo tài khoản.
  - **Trải nghiệm cho bé & công bằng bảo mật**: khi hết giờ xem, khi mua thuốc hồi sức cho Dieppie, khi mở Rương báu 10 giây hoặc khi mở tập phim VIP, bé bấm chọn 1 trong 4 nút màu lớn (cao tối thiểu `56 px`), có nút **"Nghe đọc"** dùng trình đọc giọng nói của trình duyệt (`vi-VN` hoặc `en-US` cho câu Tiếng Anh). Chọn sai sẽ hiện nhẹ nhàng đáp án đúng và giải thích ngắn (`"Gần đúng rồi! Đáp án là C: 12 nè"`). Máy chủ chọn câu tránh lặp 30 câu gần nhất của bé, trộn thứ tự A–D và giữ đáp án trong token mã hóa dùng một lần; giới hạn 10 lần sai trong 10 phút (`429`).
  - **Báo cáo học tập theo tuần & gợi ý đổi lớp**: lưu từng lượt trả lời theo bé và bộ câu hỏi, hiển thị tổng quan 7 ngày (số câu đã làm, % đúng, các câu bé hay nhầm nhất). Hệ thống gợi ý nâng lớp khi đúng `> 90%` qua ít nhất `20` câu hoặc hạ lớp khi đúng `< 50%` qua ít nhất `10` câu; phụ huynh luôn xác nhận bằng mã PIN, không bao giờ tự đổi ngầm.
- Bộ tính năng phụ huynh cho hồ sơ trẻ em trong trang Cài đặt tài khoản và giao diện trẻ em:
  - **Bảng nhiệm vụ đời thực**: phụ huynh tạo nhiệm vụ (1–120 phút thưởng, lặp mỗi ngày hoặc làm một lần, chọn từng bé nhìn thấy); bé bấm "Nộp nhiệm vụ" (tối đa 1 lượt chờ duyệt mỗi nhiệm vụ/bé) và ba mẹ duyệt/từ chối bằng mã PIN từ hồ sơ chính hoặc ngay trên máy ở chế độ trẻ em để cộng mục `BOUNTY` vào sổ cái hôm nay.
  - **Két sắt phim cuối tuần**: khóa phim lẻ từ thứ Hai đến thứ Sáu theo giờ `Asia/Ho_Chi_Minh`; thanh tiến độ "Vé xem phim" tự tăng `+20%` mỗi ngày bé dừng xem trước khi hết giờ và `+20%` mỗi ngày trả lời đúng đủ 3 câu hỏi thưởng giờ (tối đa `100%`), mở khóa tự động vào thứ Bảy & Chủ Nhật khi đạt `100%` hoặc khi ba mẹ mở thủ công bằng mã PIN.
  - **Phim VIP theo độ khó**: phụ huynh đánh dấu phim bộ VIP riêng cho từng bé; mỗi tập VIP yêu cầu bé trả lời đúng 1 câu hỏi ở khối lớp kế tiếp (hoặc lớp cao nhất sẵn có) để mở khóa tập đó trong hôm nay.
- Tính năng tương tác trên trình phát dành riêng cho chế độ trẻ em (phụ huynh bật/tắt và chỉnh ngưỡng cho từng bé bằng mã PIN): Dieppie canh giờ nghỉ mắt theo thời gian xem liên tục (vui vẻ → đổ mồ hôi → chóng mặt → mỏi mắt, chọn cho Dieppie ngủ khóa cứng phía máy chủ hoặc trả lời 1 câu hỏi mua thuốc hồi sức cho Dieppie), Rương báu bay ngang sau mỗi 5–7 phút xem (trả lời nhanh 1 câu hỏi trong 10 giây cộng +3 phút, combo 3 tặng thêm +1 phút vào sổ cái `LOOT` theo trần thưởng ngày) và Xếp lại cốt truyện để qua tập (câu hỏi ôn tập dựng từ cảnh/phân đoạn/nhân vật/tên tập có sẵn để mở khóa tập kế tiếp).
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
