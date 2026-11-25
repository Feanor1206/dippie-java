# Kịch bản Demo 5 phút: Dippie (Web gia đình và Dippie Admin JavaFX)

Thời lượng dự kiến là 5 phút (300 giây), khớp thời gian thuyết trình của môn học. Phân vai trên máy trình chiếu như sau:

- Thành viên 1 (nhóm trưởng, backend): mở lời, giữ nhịp chuyển phần và kết bài.
- Thành viên 2 (frontend và UX): thao tác đăng nhập `user`, lọc tâm trạng, tìm kiếm và chấm điểm.
- Thành viên 3 (CineBot và gợi ý): gõ câu hỏi vào CineBot, mở đúng mốc cảnh và thử câu ngoài danh mục.
- Thành viên 4 (JavaFX và triển khai): mở quản trị Web, chuyển sang cửa sổ Dippie Admin (JavaFX) thao tác Danh mục, Hoàn tác và Xuất CSV.

## 0. Chuẩn bị máy trước khi lên bảng (3 đến 5 phút)

1. Cách nhanh nhất là nhấp đúp `CHAY_DIPPIE.bat` ở thư mục gốc của gói dự án (xem thêm `HUONG_DAN_CHAY.md`; gói nộp nén bằng `.\scripts\make-release-zip.ps1`). Script tự tải JDK 21 di động vào `.tools\jdk` nếu máy chưa có, khởi động bản demo trên `http://localhost:3000/`, tự nạp đủ 6 gói danh mục trong `data/catalog-by-series/`, rồi mở luôn trình duyệt cùng cửa sổ ứng dụng desktop Dippie Admin.
   Nếu muốn tự khởi động từng phần trong PowerShell khi phát triển:
   ```powershell
   .\start-java.ps1
   .\scripts\import_preview_catalog.ps1
   .\start-desktop.ps1
   ```
2. Mở nhanh hai địa chỉ sau trên tab phụ (hoặc kiểm tra bằng `curl`) để chắc chắn hệ thống đã lên:
   - `http://localhost:3000/api/health`: trả mã HTTP `200`, trạng thái `UP`.
   - `http://localhost:3000/api/assistant/status`: kiểm tra `searchReady: true`, `documents >= 2517`, `llmProvider: "template"` (hoặc `"deepseek"` nếu đã điền khóa API và mạng phòng học ổn).
3. Mở `http://localhost:3000/series` kiểm tra đã có đủ 6 bộ phim (806 tập): Doraemon (337 tập), Công chúa Ori (180 tập), Ấu trùng tinh nghịch Larva (104 tập), Xin Chào Jadoo (99 tập), Heo Peppa (51 tập) và Trạng Quỳnh Thời Nhí Nhố (35 tập).
4. Nhớ sẵn hai tài khoản mẫu do `DemoAccountSeeder` và `DemoHistorySeeder` tạo:
   - Tài khoản xem phim: `user` / `password123` (có sẵn 3 mốc tiến độ xem, 3 mục Xem sau, 2 mục Yêu thích và 3 đánh giá).
   - Tài khoản quản trị: `admin` / `password123`.
5. Thu nhỏ cửa sổ Dippie Admin xuống thanh tác vụ để lát Alt+Tab sang ngay. Hồ sơ `demo` bật `AUTO_SERVER=TRUE` trên H2 nên Spring Boot và Dippie Admin dùng chung tệp cơ sở dữ liệu `target\dippie-java\dippie` mà không bị khóa tệp.
6. Đóng bớt các tab không liên quan, chỉnh phóng to trình duyệt lên `110%` để hàng ghế cuối đọc rõ chữ tiếng Việt, và chép sẵn một đoạn video quay màn hình dự phòng vào máy trình chiếu.

## 1. Các bước thao tác theo mốc thời gian (5:00)

### 0:00 đến 0:45 (45 giây): Mở đầu, đăng nhập và mục "Xem tiếp"

Người phụ trách: Thành viên 1 nói mở lời, Thành viên 2 bấm trên máy.

Các bước trên máy:
1. Mở sẵn trang chủ `http://localhost:3000/` khi chưa đăng nhập. Trỏ nhanh vào khối Kho phim hoạt hình, hàng 6 bộ phim và lưới Gợi ý cho bạn (khách mới thấy 6 tập chia đều cho 6 bộ).
2. Bấm nút Đăng nhập ở góc phải thanh điều hướng.
3. Gõ `user`, mật khẩu `password123`, rồi bấm Đăng nhập.
4. Trỏ vào hàng Xem tiếp vừa hiện ở đầu trang chủ với tiến độ đã lưu sẵn (Heo Peppa Tập 1 ở mốc `01:32`, Doraemon Tập 209 ở mốc `06:00`).

Câu nói gợi ý (Thành viên 1):
> "Dạ đây là giao diện chính của Dippie chạy trên cổng 3000 với 6 bộ hoạt hình, tổng 806 tập. Khi bạn em đăng nhập tài khoản user, hàng Xem tiếp hiện ra ngay ở đầu trang để phụ huynh mở tiếp đúng đoạn bé xem dở hôm trước ạ."

### 0:45 đến 1:40 (55 giây): Lọc tâm trạng, tìm kiếm chịu lỗi gõ và chấm điểm

Người phụ trách: Thành viên 2.

Các bước trên máy:
1. Ngay trên trang chủ, phía trên mục Gợi ý cho bạn, bấm chip Nhẹ nhàng trước giờ ngủ rồi bấm tiếp chip Tập ngắn dưới 10 phút để thấy danh sách gợi ý đổi ngay theo `MoodRecommendationStrategy`.
2. Bấm mục Kho phim trên thanh điều hướng (`/search`).
3. Gõ `Trang Quynh` hoặc `nobitta` vào ô tìm kiếm rồi nhấn Enter để cho thấy hệ thống bỏ dấu tiếng Việt, hiểu bí danh và chịu lỗi gõ.
4. Chọn bộ lọc thể loại Gia đình, đổi mục sắp xếp sang Đánh giá cao nhất.
5. Bấm mở một bộ phim (Heo Peppa hoặc Larva) sang trang `/series/{id}`.
6. Bấm Lưu xem sau hoặc Yêu thích, mở khung chấm điểm và chọn 9/10. Chỉ vào điểm trung bình trên màn hình cập nhật lại ngay mà không cần tải lại trang.

Câu nói gợi ý (Thành viên 2):
> "Ở trang chủ phụ huynh có thể chọn nhanh theo tâm trạng, ví dụ tập ngắn trước giờ ngủ. Sang Kho phim em thử gõ không dấu hoặc gõ sai chính tả thì hệ thống vẫn tìm ra đúng tập. Khi bấm chấm 9 điểm ở đây, điểm trung bình cộng đồng cập nhật lại luôn không phải tải lại trang ạ."

### 1:40 đến 2:55 (75 giây): Trợ lý CineBot (SSE, mở đúng mốc cảnh và từ chối ngoài kho)

Người phụ trách: Thành viên 3.

Các bước trên máy:
1. Bấm nút Hỏi CineBot trên thanh điều hướng.
2. Gõ `Larva có bao nhiêu tập?` rồi bấm Gửi. CineBot trả lời ngay (khoảng 5 ms) là 104 tập.
3. Gõ tiếp `Cảnh Red đội kem` (hoặc `tập Doraemon nào Nobita đi vũ trụ`) rồi bấm Gửi. Chữ chạy ra theo luồng SSE (`metadata`, `token`, `done`) và hiện thẻ tập Kem (`ep_larva_s01_0001`) ở mốc `00:33`. (Nếu dùng câu Doraemon thì ra tập Chiến Tranh Vũ Trụ Dưới Mái Nhà ở `00:39`, có thể bấm thêm chip `Cảnh sau đó thì sao` để sang `03:10`.)
4. Bấm nút Xem tập ngay trên thẻ phim trong CineBot. Trình duyệt chuyển sang `/series/ser_larva?episode=ep_larva_s01_0001&segment=...&t=33000` và mở video tại đúng giây `00:33`.
5. Mở lại Hỏi CineBot và gõ nhanh hai câu thử:
   - `gợi ý phim hài nhẹ nhàng ngắn dưới 5 phút cho bé xem trước khi ngủ`: trả các tập ngắn dưới 5 phút, không lẫn video tổng hợp dài.
   - `có phim Naruto hoặc One Piece không`: CineBot từ chối rõ ràng và liệt kê lại 6 bộ có trong kho.

Câu nói gợi ý (Thành viên 3):
> "CineBot chạy cục bộ trong Java bằng chỉ mục BM25 trên hơn 2.500 tài liệu và đẩy chữ qua SSE. Khi em hỏi cảnh Red đội kem rồi bấm Xem tập, trình phát mở đúng giây thứ 33. Còn khi hỏi phim không có trong kho như Naruto thì hệ thống từ chối luôn chứ không bịa tên tập ạ."

### 2:55 đến 3:40 (45 giây): Quản trị Web (phân quyền, thống kê và nhật ký kiểm toán)

Người phụ trách: Thành viên 1 hoặc Thành viên 4.

Các bước trên máy:
1. Bấm vào tên tài khoản `user` ở góc phải, chọn Đăng xuất (phía máy chủ gọi `POST /api/auth/logout` tăng `tokenVersion` để thu hồi ngay JWT cũ).
2. Bấm Đăng nhập, gõ `admin` / `password123`. Chỉ vào 5 mục quản trị mới hiện trên thanh điều hướng: Nhập dữ liệu, Người dùng, Thống kê, Nhật ký và Báo lỗi video (`/admin/reports`).
3. Bấm Thống kê (`/admin/stats`), chỉ nhanh tổng số người dùng, 806 tập đã xuất bản, tỷ lệ xem xong và bảng top tập xem nhiều, đánh giá cao.
4. Bấm Nhật ký (`/admin/audit`), chỉ vào các dòng sự kiện `CATALOG_IMPORTED` hoặc cập nhật tài khoản được ghi tự động bởi `CatalogImportAuditListener`.

Câu nói gợi ý:
> "Khi bấm Đăng xuất thì backend thu hồi luôn token cũ ở phía máy chủ. Sang tài khoản admin, thanh điều hướng mở đủ 5 mục quản trị gồm cả Báo lỗi video. Mọi lần nhập danh mục hay đổi tài khoản đều có log lại trong Nhật ký kiểm toán, và nếu đổi quyền thì phiên đăng nhập cũ cũng hết hiệu lực ngay nhờ tăng tokenVersion ạ."

### 3:40 đến 4:35 (55 giây): Ứng dụng Desktop Dippie Admin (JavaFX và JDBC)

Người phụ trách: Thành viên 4.

Các bước trên máy:
1. Nhấn Alt+Tab chuyển sang cửa sổ Dippie Admin đã mở sẵn (từ `CHAY_DIPPIE.bat` hoặc `.\start-desktop.ps1`).
2. Nhập `admin` / `password123` rồi bấm Đăng nhập (ứng dụng kiểm tra BCrypt trực tiếp trên bảng `users` qua JDBC `PreparedStatement`).
3. Tab Tổng quan: chỉ nhanh 5 ô số liệu, biểu đồ cột phân bố điểm 1 đến 10 (`BarChart`) và hai bảng top phim lấy từ cùng tệp H2 của web.
4. Tab Danh mục: gõ thử một từ khóa vào ô Tìm danh mục, mở rộng cây Bộ phim, Mùa, Tập trên `TreeView` (cài theo mẫu Composite với `CatalogNode`) rồi bấm vào một tập để xem chi tiết bên phải.
5. Tab Người dùng: chọn một tài khoản khác `admin`, bấm Khóa tài khoản (hoặc Đổi vai trò) và xác nhận. Danh sách tự làm mới và đồng bộ sang tab Tổng quan nhờ Observer (`usersChangedProperty`). Sau đó bấm nút Hoàn tác để khôi phục trạng thái qua Command Pattern (`AccountCommand` và `CommandHistory`).
6. Xuất CSV: bấm nút Xuất CSV (trên tab Người dùng lưu mặc định `nguoi-dung.csv`), lưu ra thư mục tạm rồi mở nhanh tệp để thấy tiếng Việt hiển thị đúng (UTF-8 BOM qua `CsvExporter` và `CsvStrategy`).

Câu nói gợi ý (Thành viên 4):
> "Song song với bản web, em chạy ứng dụng Dippie Admin viết bằng JavaFX 21 và JDBC thuần nối vào cùng cơ sở dữ liệu. Ở đây có cây danh mục dùng Composite trên TreeView, nút Hoàn tác dùng Command Pattern có giao dịch JDBC, các tab tự làm mới bằng Observer và xuất báo cáo CSV bằng luồng I/O ạ."

### 4:35 đến 5:00 (25 giây): Chốt bài và chuyển sang hỏi đáp

Người phụ trách: Thành viên 1.

Các bước trên máy: chuyển về Slide 14 (slide tổng kết) hoặc để hai cửa sổ Web và Dippie Admin cạnh nhau trên màn hình.

Câu nói gợi ý (Thành viên 1):
> "Dạ hệ thống hiện có 589 ca kiểm thử backend JUnit (1 ca bỏ qua trên Windows do tạo symlink cần quyền quản trị), 286 ca frontend, 33 ca desktop JavaFX, 36/36 phép kiểm thử khói trên bản dựng cuối đều đạt, điểm đánh giá UX tăng từ 3,2 lên 8,2 trên 10 sau 3 vòng. Ngoài Docker Compose với Caddy HTTPS, nhóm còn đóng gói sẵn `CHAY_DIPPIE.bat` để nhấp đúp chạy thử ngay. Nhóm em xin kết thúc phần trình bày, cảm ơn thầy và các bạn đã lắng nghe, mong nhận được góp ý từ thầy ạ."

## 2. Bộ câu hỏi CineBot dự phòng (đã chạy thử trên dữ liệu demo)

| Mục đích | Câu gõ vào CineBot | Kết quả trên dữ liệu demo |
| --- | --- | --- |
| Đếm số tập | `Larva có bao nhiêu tập?` | Trả 104 tập (khoảng 5 ms). |
| Bí danh và số tập | `Lava mùa 1 tập 46` | Hiểu bí danh `Lava` là Larva, trả tập Lò xo (`ep_larva_s01_0046`). |
| Tìm cảnh và mốc giây | `Cảnh Red đội kem` | Trả tập Kem (`ep_larva_s01_0001`), mốc `00:33`, bấm Xem tập mở đúng giây 33. |
| Hỏi tiếp nhiều lượt | `tập Doraemon nào Nobita đi vũ trụ` rồi bấm chip `Cảnh sau đó thì sao` | Mở tập Chiến Tranh Vũ Trụ Dưới Mái Nhà (`00:39`), bấm tiếp chuyển sang `03:10` rồi `05:43`. |
| Tình tiết Jadoo | `Jadoo sợ tăng cân` | Trả tập Muốn ăn nhưng lại sợ tăng cân (`ep_jadoo_s01_0002`). |
| Tóm tắt tập | `tóm tắt tập 3 Peppa` | Trả tóm tắt tập Best Friend (`ep_peppa_pig_s01_0003`). |
| Lọc thời lượng ngắn | `gợi ý phim hài nhẹ nhàng ngắn dưới 5 phút cho bé xem trước khi ngủ` | Trả 6 tập ngắn dưới 5 phút, không lẫn tuyển tập dài. |
| Phim tương tự | `phim giống Larva` | Bỏ Larva ra, trả 5 bộ còn lại trong kho. |
| Ngoài danh mục | `có phim Naruto hoặc One Piece không` | Từ chối lịch sự, không bịa tập, nhắc lại 6 bộ sẵn có. |
| Chào hỏi | `xin chào bạn là ai` | Trả lời giới thiệu CineBot ở chế độ `chitchat`. |

## 3. Xử lý nhanh nếu gặp trục trặc lúc demo

| Sự cố | Dấu hiệu | Cách xử lý tại chỗ |
| --- | --- | --- |
| 1. Mất Wi-Fi hoặc mạng phòng học yếu | Ảnh YouTube hoặc khung video YouTube không tải; gọi DeepSeek bị chậm. | 1. Nói rõ toàn bộ Dippie chạy cục bộ (`localhost:3000`, H2, BM25 trên RAM, CineBot `template`, JavaFX JDBC) nên tìm kiếm, gợi ý, thư viện, tiến độ, CineBot, quản trị Web và Dippie Admin vẫn chạy bình thường.<br>2. Ở bước mở mốc `00:33`, chỉ vào URL trên thanh địa chỉ (`?episode=ep_larva_s01_0001&t=33000`) và thuộc tính `src` của iframe (`start=33&enablejsapi=1`) trong DevTools.<br>3. Nếu máy có sẵn tệp MP4 Xin Chào Jadoo trong `media/`, mở một tập Jadoo để chạy phát video Byte Range (`206 Partial Content`) không cần mạng. |
| 2. YouTube chặn nhúng trên máy chiếu | Khung YouTube báo "Video không có sẵn". | 1. Mở tập Công chúa Ori Mùa 5 Tập 1 (Nhiệm Vụ Bất Khả Thi) để giải thích cơ chế `canEmbed: false` (hiện nhãn Chỉ mở trên YouTube và nút mở liên kết gốc).<br>2. Mở đoạn video quay màn hình dự phòng đã lưu sẵn trên máy để cho thấy cảnh nhảy đúng mốc `00:33` và tự chuyển tập. |
| 3. DeepSeek API chậm hoặc lỗi | CineBot chờ lâu hoặc báo lỗi API ngoài. | Giữ mặc định `DIPPIE_LLM_PROVIDER=template` khi demo (3 đến 13 ms, không phụ thuộc mạng). Nếu đang bật `deepseek`, `DeepSeekAnswerGenerator` cũng tự rơi về `TemplateAnswerGenerator`. |
| 4. Trùng cổng `3000` hoặc H2 bị khóa tệp | `start-java.ps1` báo cổng 3000 bận hoặc JavaFX không mở được H2. | 1. Nếu chạy bằng `CHAY_DIPPIE.bat`, script tự chọn cổng trống tiếp theo (`3001`, `3002`...) và in địa chỉ cuối màn hình; nếu chạy tay thì đổi bằng `$env:PORT = "3001"; .\start-java.ps1`.<br>2. Kiểm tra URL H2 có `AUTO_SERVER=TRUE` (mặc định của hồ sơ `demo`) và luôn chạy Spring Boot trước khi mở Dippie Admin. |
| 5. Dữ liệu demo bị sửa lộn xộn trước giờ thi | Tài khoản hoặc lịch sử mẫu không còn nguyên. | Chạy `CHAY_DIPPIE.bat -Stop` rồi `CHAY_DIPPIE.bat -Reset` (xác nhận `Y`) và nhấp đúp `CHAY_DIPPIE.bat` lại để tạo mới `target\dippie-java` và nạp đủ 6 gói danh mục trong chưa tới 30 giây. |
