# Hướng dẫn chạy Dippie

Bạn có thể mở và dùng thử toàn bộ Dippie (trang web xem phim hoạt hình có sẵn 6 bộ phim cùng ứng dụng quản trị máy tính Dippie Admin) bằng cách nhấp đúp chuột, không cần tự cài Java, Node.js hay cơ sở dữ liệu.

## 1. Cần chuẩn bị gì trước khi chạy?

- Máy chạy Windows 10 hoặc Windows 11 (64-bit).
- Có mạng Internet ở lần chạy đầu để script tải công cụ và thư viện cần thiết; từ lần thứ hai trở đi khởi động nhanh hơn nhiều.
- Ổ đĩa còn trống khoảng 1 GB để chứa bộ Java di động, thư viện Maven và dữ liệu mẫu.
- Không cần quyền quản trị viên (Administrator) và không cài thêm phần mềm nào vào Windows.

## 2. Ba bước để chạy Dippie trên Windows

1. Giải nén gói dự án: nhấp chuột phải vào tệp `Dippie-*.zip`, chọn Extract All... (hoặc Giải nén tất cả...) rồi đưa vào một thư mục thật trên máy, ví dụ `Desktop\Dippie` hoặc `D:\Dippie`. Đừng mở chạy trực tiếp bên trong cửa sổ xem trước của tệp `.zip`.
2. Mở thư mục vừa giải nén và nhấp đúp vào tệp `CHAY_DIPPIE.bat` ở thư mục gốc.
3. Chờ trình duyệt tự mở: một cửa sổ dòng lệnh sẽ hiện ra để chuẩn bị Java, khởi động máy chủ và nạp sẵn 6 bộ phim hoạt hình. Xong xuôi, trình duyệt tự mở trang Dippie và cửa sổ ứng dụng quản trị Dippie Admin (JavaFX) cũng hiện lên.

## 3. Lần chạy đầu tải những gì và mất bao lâu?

- Java 21 di động (~200 MB): nếu máy chưa có sẵn JDK 21 trở lên, trình khởi chạy tự tải Eclipse Temurin JDK 21 và đặt vào thư mục `.tools\jdk` ngay trong dự án.
- Thư viện máy chủ và ứng dụng quản trị (~150 đến 250 MB): Maven tự tải các thư viện Java về thư mục đệm cá nhân `%USERPROFILE%\.m2`.
- Giao diện web: đã đóng gói sẵn trong `frontend\dist`, nên máy không cần Node.js.
- Dữ liệu phim mẫu: tự tạo cơ sở dữ liệu demo (H2) tại `target\dippie-java` rồi tự nhập đủ 6 bộ phim (`jadoo`, `larva`, `ori`, `doraemon`, `trang_quynh`, `peppa_pig`).

Thời gian chờ ước tính:

- Lần chạy đầu: khoảng 2 đến 6 phút tùy tốc độ mạng (trình khởi chạy chờ tối đa 10 phút và in dấu chấm tiến trình).
- Từ lần thứ hai trở đi: khoảng 15 đến 30 giây vì Java, thư viện và dữ liệu phim đều đã có trên máy.

## 4. Tài khoản mẫu để đăng nhập

Cả hai tài khoản dùng chung mật khẩu `password123`:

| Vai trò | Tên đăng nhập | Mật khẩu | Dùng ở đâu |
| --- | --- | --- | --- |
| Người xem | `user` | `password123` | Trang web Dippie |
| Quản trị viên | `admin` | `password123` | Trang web Dippie (mục Quản trị) và ứng dụng Dippie Admin |

## 5. Cách dừng chương trình và đặt lại dữ liệu (reset)

### Dừng toàn bộ Dippie

Bạn có thể chọn một trong hai cách:

- Đóng cửa sổ Dippie Backend (đang thu nhỏ dưới thanh tác vụ Windows) và đóng cửa sổ Dippie Admin.
- Mở PowerShell hoặc Command Prompt trong thư mục dự án rồi chạy:
  ```bat
  CHAY_DIPPIE.bat -Stop
  ```

### Đặt lại dữ liệu demo về trạng thái ban đầu

Nếu đã sửa hoặc xóa thử dữ liệu và muốn nạp lại 6 bộ phim sạch như lúc mới tải, chạy lệnh sau trong thư mục dự án:

```bat
CHAY_DIPPIE.bat -Reset
```

Khi màn hình hỏi xác nhận, gõ `Y` (hoặc `C`) rồi nhấn Enter. Lệnh này xóa thư mục `target\dippie-java` để lần nhấp đúp `CHAY_DIPPIE.bat` tiếp theo tự tạo lại cơ sở dữ liệu và nhập đủ 6 danh mục phim.

### Một số tùy chọn phụ

- `CHAY_DIPPIE.bat -NoDesktop`: chỉ chạy trang web, không mở ứng dụng quản trị JavaFX.
- `CHAY_DIPPIE.bat -NoBrowser`: khởi động hệ thống nhưng không tự mở trình duyệt.

## 6. Năm sự cố thường gặp và cách xử lý

1. Windows hiện hộp thoại xanh "Windows protected your PC" (SmartScreen): do tệp `.bat` tải từ Internet chưa có chữ ký số thương mại nên Windows hỏi xác nhận. Nhấn dòng chữ More info (Thông tin khác), sau đó nhấn nút Run anyway (Vẫn chạy).
2. Phần mềm diệt virus (Windows Defender, Bkav, Avast...) chặn tải JDK hoặc chặn chạy script: chọn Allow (Cho phép) khi hiện thông báo, hoặc thêm thư mục vừa giải nén Dippie vào danh sách loại trừ (Exclusion). Nếu việc tải JDK bị ngắt giữa chừng, xóa thư mục `.tools\jdk` (nếu có) rồi nhấp đúp `CHAY_DIPPIE.bat` lại.
3. Cổng 3000 đang có chương trình khác dùng: bạn không cần sửa gì, trình khởi chạy tự tìm và chuyển sang cổng trống tiếp theo (như `3001`, `3002`...). Xem đúng địa chỉ trang web trong khung tóm tắt màu xanh ở cuối, ví dụ `http://localhost:3001/`.
4. Máy chưa có Internet ở lần chạy đầu: lần đầu bắt buộc cần mạng để tải JDK 21 di động và thư viện Maven. Kết nối Wi-Fi hoặc mạng dây rồi chạy lại `CHAY_DIPPIE.bat` (những phần đã tải trước đó sẽ được giữ lại). Nếu mạng trường hoặc công ty chặn Adoptium hoặc Maven Central, bạn có thể phát mạng 4G từ điện thoại cho lần chạy đầu.
5. Tường lửa Windows (Windows Defender Firewall) hỏi quyền cho Java: máy chủ Dippie mở cổng nội bộ trên máy nên Windows hỏi xác nhận mạng. Đánh dấu chọn Private networks (Mạng riêng tư) rồi nhấn Allow access (Cho phép truy cập).

Nếu cần xem lỗi chi tiết, nhật ký được ghi tại `.tools\run\backend.log` (máy chủ web) và `.tools\run\desktop.log` (ứng dụng quản trị).

## 7. Chạy trên macOS và Linux

Sau khi giải nén gói dự án, mở Terminal, đi vào thư mục vừa giải nén rồi chạy:

```bash
bash run-all.sh
```

Script tự tải JDK 21 di động khớp với macOS hoặc Linux (Intel hay Apple Silicon/ARM64), chạy máy chủ demo, nạp 6 bộ phim, mở trình duyệt và mở ứng dụng quản trị JavaFX.

Các lệnh điều khiển tương ứng trên macOS/Linux:

- Dừng toàn bộ: `bash run-all.sh --stop`
- Đặt lại dữ liệu demo: `bash run-all.sh --reset`
- Chỉ chạy trang web, không mở app desktop: `bash run-all.sh --no-desktop`
