# Cơ sở dữ liệu

Dippie dùng MySQL 8.0 khi chạy thật và H2 (chế độ tương thích MySQL) cho chế độ `demo`. Tệp `schema/01_init.sql` là bản gốc của lược đồ MySQL.

| Thư mục | Nội dung |
| --- | --- |
| `schema/` | Tạo lược đồ mới từ `01_init.sql` (khớp với bản Flyway mới nhất) |
| `migrations/` | Nâng cấp schema cũ theo thứ tự 02 đến 15 khi chạy SQL thủ công |
| `objects/` | View, hàm, thủ tục, trigger, tài khoản và bài thực hành giao dịch |
| `seeds/` | Dữ liệu phim, tài khoản và thư viện mẫu |
| `queries/` | 20 truy vấn mẫu, câu SQL đối chiếu và phép đo 100.000 tập |

## Dựng MySQL mới

1. Tạo một schema trống dùng `utf8mb4`, rồi nạp `schema/01_init.sql` vào đó.
2. Nạp lần lượt `views.sql`, `functions.sql`, `procedures.sql`, `triggers.sql` và `security.sql`. Script PowerShell và Docker Compose đã làm sẵn bước này. Tệp `transactions_demo.sql` để chạy riêng khi thực hành.
3. Nếu dựng schema demo, nạp thêm `seeds/01_seed_movies.sql` rồi `seeds/02_seed_50_users.sql`.
4. Chạy backend Spring Boot với profile `mysql` và thử gọi `GET /api/health`. Tiếp theo nhập gói danh mục ở trang `/admin/import` hoặc bằng `scripts/import_preview_catalog.ps1`, rồi kiểm tra trạng thái snapshot và một tập vừa nhập.

Khi khởi tạo một volume MySQL mới, Docker Compose trong `infrastructure/docker-compose.yml` tự nạp `01_init.sql` và toàn bộ `database/objects/*.sql`.

## Đối tượng MySQL và quyền tài khoản

Docker Compose nạp `01_init.sql`, 4 tệp đối tượng và tệp phân quyền. Bạn điền `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` (cho `dippie_app`), `DIPPIE_DB_READONLY_PASSWORD` và `DIPPIE_DB_ADMIN_PASSWORD` trong tệp `.env` của Compose. Nếu dùng lại volume cũ, chạy các migration còn thiếu theo thứ tự số rồi nạp lại đối tượng bằng script PowerShell. Đặt tệp cấu hình MySQL của tài khoản cài đặt bên ngoài Git và khai báo 3 biến `DIPPIE_DB_APP_PASSWORD`, `DIPPIE_DB_READONLY_PASSWORD`, `DIPPIE_DB_ADMIN_PASSWORD` trong phiên PowerShell trước khi chạy:

```powershell
.\scripts\load_database_objects.ps1 -Database movie_rcm_db -Port 3306 -DefaultsFile $dippieClientOptions
```

| Tài khoản | Phạm vi |
| --- | --- |
| `dippie_app` | Đọc và ghi hàng, gọi routine trong schema Dippie |
| `dippie_readonly` | Đọc bốn view, gồm lịch sử xem theo ID người dùng |
| `dippie_admin` | Quản trị trong schema Dippie |

Ba tên tài khoản này gắn riêng cho một schema trên mỗi máy chủ. Chạy lại `security.sql` sẽ cập nhật mật khẩu và cấp lại quyền cho cả ba. Tài khoản cài đặt là người định nghĩa view và trigger, còn routine chạy bằng quyền của người gọi ([Quyền của đối tượng MySQL](https://dev.mysql.com/doc/refman/8.0/en/stored-objects-security.html)).

Chi tiết vài đối tượng đáng chú ý:

- `sp_add_favorite` trả lại mục yêu thích có sẵn nếu cặp người dùng và phim đã được lưu.
- `sp_next_episode` ưu tiên tập đang xem dở gần nhất, sau đó mới xét tập chưa hoàn tất theo thứ tự mùa và số tập.
- `sp_publish_episode` yêu cầu đủ tên, tóm tắt, số tập, thời lượng và thể loại. Trạng thái mới sẽ hiện ngay trong view và lần xuất catalog sau, còn `raw_json` vẫn giữ nguyên bản nhập gốc.
- API lưu tiến độ lấy giá trị nhỏ hơn giữa thời lượng của tập và thời lượng của video. Trigger MySQL cũng kiểm tra giới hạn của tập khi thao tác SQL trực tiếp.

Migration `09_audit_log.sql` thêm bảng ghi ảnh chụp cũ/mới của tập lúc cập nhật và ảnh chụp cũ lúc xóa, kèm cột `mysql_user` lưu tài khoản phiên kết nối. Trigger chỉ chạy khi thao tác trực tiếp vào `catalog_episodes`. Khi MySQL xóa dây chuyền theo khóa ngoại, trigger xóa trên bảng con sẽ không được gọi ([Quy tắc trigger MySQL](https://dev.mysql.com/doc/refman/8.0/en/create-trigger.html)).

### Thực hành giao dịch

Mở hai kết nối Workbench tới schema demo có seed và kiểm tra phim `the-last-orbit` đã tồn tại. Chạy từng khối trong `transactions_demo.sql` theo dòng `exercise` của khối:

1. Phiên A chạy `savepoint_walkthrough`. Điểm hiện tại được khóa bằng `FOR UPDATE`, sửa tạm rồi hoàn tác về điểm ban đầu bằng `ROLLBACK TO SAVEPOINT`.
2. A chạy `read_committed_begin`, B chạy `writer_change` rồi commit. A chạy `reader_finish` và nhìn thấy điểm B vừa commit. B chạy `writer_restore`.
3. A chạy `repeatable_read_begin`, B chạy `writer_change`. A chạy `reader_finish` nhưng vẫn thấy điểm ở lần đọc đầu. B chạy `writer_restore` để trả về điểm ban đầu ([Mức cô lập InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)).

## Quan hệ danh mục

- Mỗi tập thuộc một bộ. Khi tập có mùa, khóa ngoại `(series_id, season_id)` ép mùa đó phải thuộc cùng bộ; tập chưa phân mùa thì để `season_id` là `NULL`. Muốn xóa hoặc đổi bộ của một mùa, cần xử lý các tập đang trỏ tới mùa đó trước.
- `catalog_episode_assets` nối tập với các tệp video có phạm vi phát tương ứng. Khóa chính `(episode_id, media_asset_id)` giúp tra tệp theo tập và tránh nối trùng. `coverage_json` giữ mốc thời gian, phạm vi và dữ liệu xuất gói.
- Khi xem trước hoặc nhập gói, hệ thống kiểm tra tập, mùa và các tập được nhắc tới trong phạm vi tệp. Nhập thành công thì metadata và mọi liên kết được ghi trong cùng một giao dịch.
- `catalog_episode_characters` và `catalog_episode_genres` dùng khóa chính ghép và khóa ngoại để tra tập theo nhân vật hoặc thể loại. Lúc sửa một tập, đợt nhập thay toàn bộ liên kết của tập trong cùng giao dịch với bản ghi.
- `catalog_series_genres`, `catalog_series_characters` và `catalog_character_series` tách các quan hệ nhiều-nhiều giữa bộ và nhân vật. Danh sách nhân vật giới thiệu của một bộ và danh sách những bộ mà một nhân vật xuất hiện là hai quan hệ tách biệt. `movie_genres` nối phim với tên thể loại viết thường, đã cắt khoảng trắng đầu cuối trong `movie_genre_terms`. Luồng nhập ghi các bảng nối trong cùng giao dịch, còn cột JSON/CSV cũ giữ tương thích cho API và nguồn gói.
- Danh sách xem sau, yêu thích và điểm chấm đều tham chiếu `movies`. API danh sách lấy tên phim và ảnh hiện tại bằng JOIN. Xóa phim sẽ dọn các mục phụ thuộc; xóa tệp danh mục sẽ dọn tiến độ và cảnh gắn với tệp đó; xóa phân đoạn hoặc tệp liên quan thì đặt khóa ngoại tùy chọn về `NULL`.
- `catalog_episodes.publication_status` nhận trạng thái lúc nhập. Chỉ mục `idx_cat_ep_publication` hỗ trợ truy vấn lấy các tập `ready` cho CineBot.
- Mỗi tập có tối đa một phân đoạn ở mỗi vị trí. Mốc đầu và mốc cuối của đoạn có thể cùng để trống; nếu có mốc, điểm đầu phải từ 0 trở lên và điểm cuối phải lớn hơn điểm đầu.
- `catalog_text_tracks` nối phụ đề theo tệp video hoặc theo cặp tập và nguồn cụ thể. Với tệp phụ đề lưu ngoài DB, đường dẫn và mã SHA-256 dùng để nhận diện đúng bản đã nạp.

## Nâng cấp MySQL đang chạy

Tạm dừng backend và mọi tiến trình ghi danh mục, sao lưu bằng tài khoản cài đặt, rồi chạy các migration còn thiếu theo đúng thứ tự số. Với schema đã qua bước 06, thứ tự chạy là `07_catalog_publication_and_tracks.sql`, `08_catalog_value_domains.sql`, `09_audit_log.sql`, `10_catalog_query_indexes.sql`, `11_account_controls_and_admin_audit.sql`, `12_youtube_watch_progress.sql`, `13_nullable_movie_release_year.sql`, `14_query_performance_indexes.sql`, `15_terms_reports_and_password_reset.sql`. Chạy xong thì nạp lại các đối tượng và mở lại backend Spring Boot bằng tài khoản ứng dụng ([Sao lưu và khôi phục](../docs/database/backup-restore.md)).

Trong MySQL Workbench, chọn đúng schema và kiểm tra xem có tập nào bị thiếu mùa hoặc trỏ sang mùa của bộ khác không:

```sql
SELECT e.id, e.series_id, e.season_id
FROM catalog_episodes AS e
LEFT JOIN catalog_seasons AS s ON s.id = e.season_id AND s.series_id = e.series_id
WHERE e.season_id IS NOT NULL AND s.id IS NULL;
```

Khi truy vấn trả về 0 dòng, chạy [migration 06](migrations/06_catalog_integrity.sql) trên schema đó. Tệp này chạy lại nhiều lần được. Nếu MySQL báo lỗi liên kết, đối chiếu các mã trả về ở trên với nguồn gốc rồi sửa trước khi chạy lại.

[Migration 07](migrations/07_catalog_publication_and_tracks.sql) cập nhật phần catalog trên MySQL 8.0.16 trở lên. Bảng kết quả đầu tiên liệt kê `table_name`, `row_id`, `reference_column` và `missing_id` của các dòng mồ côi. Nếu thấy có dòng lỗi, MySQL CLI dừng ở bảng kiểm tra tạm để bạn đối chiếu với nguồn và sửa trên đúng schema trước khi chạy lại (nhớ chạy MySQL CLI ở chế độ dừng khi gặp lỗi). Script sẽ điền các bảng nối từ mã có sẵn trong dữ liệu cũ và bỏ hai cột tiêu đề/ảnh đã chuyển sang JOIN.

[Migration 08](migrations/08_catalog_value_domains.sql) đổi `release_date` của mùa và tập sang kiểu `DATE`, đổi `retrieved_at` của nguồn sang `DATETIME(6)` theo UTC. Bước kiểm tra đầu tiên sẽ in ra mã và giá trị ngày giờ sai trước khi đổi kiểu cột. Ngày phát hành cần đủ năm, tháng, ngày; nếu chỉ biết năm thì thông tin đó vẫn nằm trong bản gốc, còn cột ngày phát hành để trống. Các mốc thời gian có múi giờ được đổi về UTC, chuỗi cũ thiếu múi giờ thì hiểu là UTC. Hai tên loại cũ `standard` và `episode` được đổi thành `regular` ở các cột trước đây dùng chúng.

Danh sách loại nội dung và trạng thái xuất bản lấy theo [hợp đồng catalog](../docs/contracts/animation-catalog-v1/dippie.catalog.v1.schema.json). Trạng thái gói `COMMITTED`, các trạng thái snapshot và vai trò tài khoản khớp với luồng ghi của backend. Phép so sánh theo `HEX` giữ đúng chữ hoa, chữ thường và khoảng trắng. Migration này cũng khai báo thêm các ràng buộc CHECK cho giá trị số, khoảng thời gian và cận thang đo.

Trên MySQL 8.0.18 trở lên, bạn có thể kiểm tra nhanh bằng các câu sau (có `EXPLAIN ANALYZE`):

```sql
SELECT episode_id, position, COUNT(*) AS occurrences
FROM catalog_segments
GROUP BY episode_id, position
HAVING COUNT(*) > 1;

SELECT id, episode_id, episode_start_ms, episode_end_ms
FROM catalog_segments
WHERE (episode_start_ms IS NULL) <> (episode_end_ms IS NULL)
   OR episode_start_ms < 0
   OR episode_end_ms <= episode_start_ms;

EXPLAIN ANALYZE
SELECT id, raw_json FROM catalog_episodes WHERE publication_status = 'ready';
```

Migration 09 thêm `audit_log`. Migration 10 thêm chỉ mục để tổng hợp tập theo trạng thái, bộ và thời lượng, đồng thời bỏ 5 chỉ mục trùng tiền tố của UNIQUE. Chạy xong thì nạp lại các đối tượng để cập nhật `v_genre_stats` ([Truy vấn và phép đo](queries/README.md)).

Nếu cần quay về bản ứng dụng cũ, khôi phục bản sao lưu trước migration vào một schema mới rồi kiểm thử ứng dụng trên schema đó. Chế độ demo dùng H2 với `ddl-auto: update` nên tự dựng lược đồ cục bộ.

## Quản lý phiên bản lược đồ bằng Flyway

Backend Spring Boot bật Flyway trên hai profile MySQL (`mysql` và `prod`), dùng các tệp trong `backend/springboot/src/main/resources/db/migration/`:

- `V1__baseline_schema.sql`: tương đương `database/schema/01_init.sql` sau khi đã gộp đủ các bản nâng cấp từ `02` đến `13`.
- `V2__add_user_terms_accepted_at.sql`: thêm cột `users.terms_accepted_at` theo kiểu lũy đẳng.
- `V3__add_playback_reports.sql`: tạo bảng `playback_reports` lưu báo cáo sự cố phát video.
- `V4__add_password_reset_tokens.sql`: tạo bảng `password_reset_tokens` lưu mã băm token đặt lại mật khẩu.
- `V5__add_query_performance_indexes.sql`: thêm 10 chỉ mục tăng tốc truy vấn theo kiểu lũy đẳng cho MySQL 8 (tương ứng `14_query_performance_indexes.sql`).

Cách Flyway phối hợp với thư mục `database/`:

1. Khởi tạo mới qua Flyway (`mysql` hoặc `prod`): trên schema MySQL trống, Flyway chạy tuần tự từ `V1` đến `V5`, sau đó Hibernate đối chiếu lược đồ bằng `ddl-auto: validate`. Tài khoản chạy Flyway cần có quyền DDL (ví dụ `dippie_admin` hoặc tài khoản tương đương khi nâng cấp).
2. Cơ sở dữ liệu đã dựng bằng script cũ: nhờ `baseline-on-migrate: true` và `baseline-version: 1`, Flyway đánh dấu schema hiện tại là mốc `V1` mà không chạy lại `V1`, rồi chỉ chạy từ `V2` trở đi. Các tệp từ `V2` đến `V5` đều viết lũy đẳng nên vẫn chạy ổn nếu schema đã nạp từ `database/schema/01_init.sql` mới nhất (chẳng hạn qua Docker Compose).
3. Giữ đồng bộ `database/schema/01_init.sql` và `database/migrations/`: tệp `01_init.sql` luôn phản ánh đúng phiên bản Flyway mới nhất để Docker Compose và các bài thực hành SQL dùng luôn. Tệp `database/migrations/14_query_performance_indexes.sql` giữ 10 chỉ mục hiệu năng (`V5`), còn `database/migrations/15_terms_reports_and_password_reset.sql` giữ bản nâng cấp thủ công tương đương từ `V2` đến `V4`.
4. Profile `demo` và `test`: Flyway tắt (`spring.flyway.enabled: false`). Profile `demo` vẫn dùng H2 với Hibernate `ddl-auto: update` để chạy ngay mà không cần MySQL.

## Kiểm thử

Kiểm thử lược đồ và tầng dữ liệu nằm trong bộ test backend Spring Boot (chạy từ `backend/springboot/`):

```powershell
.\mvnw.cmd -B test
```

## Tài liệu lược đồ

- [ERD và bản số của các khóa ngoại](../docs/database/erd.md)
- [Khóa, phụ thuộc hàm và phân tích chuẩn hóa](../docs/database/normalization.md)
- [Từ điển dữ liệu từng cột](../docs/database/data-dictionary.md)
- [Quy trình và kết quả sao lưu và khôi phục](../docs/database/backup-restore.md)

## Mốc kiểm thử 100.000 tập

Phép đo chạy trên MySQL 8.0.46 cục bộ ở một schema riêng với 100.000 tập giả lập, 1.000 bộ, 100.000 liên kết tập và tệp, trong đó khoảng 7,7% là tập nháp. Mỗi trường hợp làm nóng 2 lần rồi lấy trung vị của 7 lần đo. Phép đo chỉ tính đường đọc MySQL, tách riêng bước tải chỉ mục CineBot.

| Truy vấn ở 100.000 tập | Trước | Sau | Số bản ghi phim được nạp sau |
| --- | ---: | ---: | ---: |
| Duyệt kho hoạt hình, trang 12 kết quả | 2.864 ms | 1.642 ms | 12 |
| Duyệt kho theo thể loại, trang 12 kết quả | 2.710 ms | 1.782 ms | 12 |
| Tìm theo một tiêu đề tập | 2.768 ms | 1.940 ms | 1 |
| Lấy tập và danh sách tệp theo mã | 6,2 ms | 6,4 ms | Không tải bảng `movies` |

Truy vấn qua chỉ mục tập-tệp chạy ổn định ở cả mốc 10.000 lẫn 100.000 tập. Phần duyệt kho sắp xếp và phân trang trực tiếp trên SQL ở MySQL. Tại mốc 100.000 tập, giao dịch thêm 1.000 tập cùng 1.000 liên kết hết khoảng 1,06 giây; nhập lại cùng gói trả kết quả lũy đẳng trong khoảng 0,19 giây; gói bị sai quan hệ bị chặn lại ngay.

## Nhập danh mục thật

Nên chạy thử trên một bản sao MySQL sau khi áp dụng migration. Chia nội dung thành các gói tự chứa theo bộ hoặc nhóm mùa đúng theo [schema `dippie.catalog.v1`](../docs/contracts/animation-catalog-v1/dippie.catalog.v1.schema.json), giữ nguyên mã của bản ghi cũ và tăng `revision` mỗi khi sửa. Gọi `POST /api/admin/catalog/validate` (hoặc dùng `scripts/import_preview_catalog.ps1`) để kiểm tra lỗi và xung đột trước lúc nhập.

Nhập xong từng gói, đối chiếu số tập và phiên bản gói trong lịch sử nhập, rồi kiểm tra trạng thái `catalog_snapshots`, thử lấy một tập theo mã và mở vài nguồn phát mẫu.
