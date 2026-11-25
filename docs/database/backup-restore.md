# Sao lưu và khôi phục MySQL

## Tạo bản sao lưu

Đặt `$dippieClientOptions` trỏ tới tệp cấu hình MySQL của tài khoản cài đặt (lưu ngoài Git, chứa tên tài khoản, thông tin kết nối và mật khẩu). Kiểm tra đúng schema trước khi chạy. Cờ `--single-transaction` lấy ảnh chụp nhất quán cho các bảng InnoDB, nhưng trong lúc dump không nên sửa cấu trúc bảng ([Cách dùng mysqldump](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)).

```powershell
$sourceSchema = 'movie_rcm_db'
$backupDirectory = 'D:/Dippie-backups'
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$dump = Join-Path $backupDirectory ('dippie-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.sql')
mysqldump "--defaults-extra-file=$dippieClientOptions" --default-character-set=utf8mb4 --single-transaction --quick --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF "--result-file=$dump" $sourceSchema
if ($LASTEXITCODE -ne 0) { throw 'Sao lưu MySQL thất bại.' }
Get-FileHash -LiteralPath $dump -Algorithm SHA256
```

Truyền tên schema như đối số bình thường của `mysqldump` để bản dump nạp sang schema khác được. Tệp dump đã gồm toàn bộ bảng và đối tượng bên trong schema, còn tài khoản MySQL cấp lại theo bước phân quyền riêng. Các tệp video, phụ đề rời và chỉ mục AI cần sao lưu kèm theo đúng phiên bản gói ở nơi lưu trữ của chúng.

## Nạp vào schema mới

Mở MySQL CLI bằng tệp cấu hình đã chuẩn bị, tạo schema mới, chọn schema đó rồi chạy `SOURCE` với đúng đường dẫn lệnh sao lưu vừa in ra. Khi chuyển sang máy chủ khác, kiểm tra lại tài khoản `DEFINER` của view và trigger; sau khi nạp dump xong thì chạy bộ nạp đối tượng và tài khoản theo cấu hình của máy chủ mới.

```sql
CREATE DATABASE dippie_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dippie_restore;
SOURCE D:/Dippie-backups/dippie-20261124-193000-000.sql;
```

Chạy đoạn SQL sau trên cả schema nguồn và schema khôi phục rồi so từng `row_count` theo tên bảng (câu lệnh này đếm trực tiếp từng bảng):

```sql
SET SESSION group_concat_max_len = 32768;
SELECT GROUP_CONCAT(
    CONCAT('SELECT ', QUOTE(table_name), ' AS table_name, COUNT(*) AS row_count FROM `',
           REPLACE(table_name, '`', '``'), '`')
    ORDER BY table_name SEPARATOR ' UNION ALL '
) INTO @dippie_counts
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SET @dippie_counts = CONCAT(@dippie_counts, ' ORDER BY table_name');
PREPARE dippie_restore_counts FROM @dippie_counts;
EXECUTE dippie_restore_counts;
DEALLOCATE PREPARE dippie_restore_counts;

SELECT COUNT(*) FROM v_published_episodes;
SELECT DISTINCT table_schema FROM information_schema.view_table_usage WHERE view_schema = DATABASE();
```

View sau khi khôi phục phải trỏ vào bảng của schema đích. Bạn có thể đếm lại số view, routine và trigger bằng `information_schema.views`, `information_schema.routines` và `information_schema.triggers`.

## Kết quả chạy thật

Mình chạy thử ngày 24/11/2026 trên MySQL 8.0.46 cục bộ: dựng schema sạch theo thứ tự `01_init.sql`, các đối tượng, quyền tài khoản và hai tệp seed. FastAPI kết nối bằng `dippie_app`, gọi `GET /api/health` trả về `200` với trạng thái `UP`. API quản trị nhập `data/catalog-by-series/catalog.larva.json`, ghi được 104 tập, 92 phân đoạn và 66 track. Chỉ mục ở chế độ `light` trả `READY`, còn API gợi ý trả 5 kết quả có chứa tập trong gói vừa nhập.

Sau đó chạy `mysqldump` từ schema đó và khôi phục sang một schema mới trên cùng máy chủ. Cả 29/29 bảng đều khớp số dòng, view trỏ đúng vào schema khôi phục và giữ đủ 4 view, 5 routine cùng 4 trigger.

| Bảng hoặc view | Nguồn | Sau khôi phục |
| --- | ---: | ---: |
| `users` | 52 | 52 |
| `movies` | 118 | 118 |
| `watchlist_items` | 13 | 13 |
| `movie_ratings` | 30 | 30 |
| `catalog_episodes` | 104 | 104 |
| `catalog_segments` | 92 | 92 |
| `catalog_text_tracks` | 66 | 66 |
| `catalog_episode_characters` | 193 | 193 |
| `catalog_episode_genres` | 312 | 312 |
| `v_published_episodes` | 104 | 104 |

Tệp dump thử nặng 1.066.344 byte, mã SHA-256 là `0c44cf50dbb7f5ea850ca98b736239eb8143478054a21831db0225e16bad937c`. Lần chạy thử này dùng schema sạch với bộ Larva; phép đo 100.000 tập xem ở [tài liệu truy vấn và đo hiệu năng](../../database/queries/README.md).
