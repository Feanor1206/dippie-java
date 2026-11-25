# Dippie Backend — Spring Boot (Java 21)

## 1. Tổng quan

Module `backend/springboot` là bản triển khai bằng Java 21 và Spring Boot 3.5 (JPA/Hibernate, Spring Security phi trạng thái dùng JWT) thay thế toàn bộ core API nghiệp vụ trước đây nằm trong `backend/fastapi/routers/core_api.py` và `backend/fastapi/services/database.py`, giữ nguyên hợp đồng HTTP trong [CONTRACT.md](CONTRACT.md).

Phân chia thành phần trong hệ thống:

- **Backend Java (`backend/springboot`, cổng `3000`)**: đảm nhiệm xác thực và phân quyền JWT, quản lý phim và danh mục chuẩn hóa, thư viện cá nhân (xem sau, yêu thích, đánh giá), tiến độ xem, gợi ý phim (`/api/recommend`), cấp vé phát video và truyền phát HTTP Byte Range, nhập/xuất danh mục cho quản trị viên, đồng thời phục vụ giao diện React đã build.
- **Frontend React (`frontend/`)**: giữ nguyên mã nguồn; sau khi build ra `frontend/dist`, Spring Boot phục vụ trực tiếp các route SPA và tài nguyên tĩnh.
- **Dịch vụ AI Python (`backend/fastapi`, cổng `8000`, tùy chọn)**: giữ lại cho các chức năng phụ thuộc hệ sinh thái ML/LLM gồm CineBot streaming SSE (`/api/chat`), tìm kiếm ngữ nghĩa và khám phá nội dung (`/v1/discover`, `/v1/search`), và đồng bộ embedding/chỉ mục (`/api/index/sync`).

Lý do phân chia: tách các luồng nghiệp vụ có yêu cầu cao về kiểm soát giao dịch, tính toàn vẹn dữ liệu, khóa đồng thời và truyền phát tệp sang nền tảng JVM có kiểu tĩnh rõ ràng; đồng thời giữ lại tầng AI phía Python nơi các thư viện embedding và suy luận đang vận hành ổn định. Hai tiến trình giao tiếp qua HTTP nội bộ, cho phép backend Java hoạt động độc lập khi chưa bật dịch vụ AI.

## 2. Sơ đồ kiến trúc

```mermaid
flowchart LR
    Browser["Trình duyệt (React SPA)"] -->|"HTTP / REST / Range / SSE :3000"| SpringBoot["Spring Boot Backend (:3000)"]
    SpringBoot -->|"JPA / Hibernate"| DB["Cơ sở dữ liệu (H2 demo / MySQL 8)"]
    SpringBoot -->|"HTTP nội bộ / SSE (/api/chat, /v1/discover, /v1/search, /api/index/sync)"| PythonAI["Dịch vụ AI Python (:8000, tùy chọn)"]
```

## 3. Cấu trúc package

Mã nguồn tổ chức theo tính năng (package-by-feature) dưới `com.dippie`:

| Package | Trách nhiệm |
| --- | --- |
| `com.dippie` | Điểm khởi chạy ứng dụng (`DippieApplication`). |
| `com.dippie.common.error` | Phân cấp ngoại lệ miền (`DomainException`, `InvalidRequestException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException`, `TooManyRequestsException`, `ServiceUnavailableException`) và bộ xử lý lỗi tập trung `GlobalExceptionHandler`. |
| `com.dippie.common.web` | Khung phản hồi chuẩn `ApiResponse` và cấu hình đồng hồ UTC `ClockConfig`. |
| `com.dippie.common.json` | Tiện ích đọc an toàn dữ liệu JSON lưu trong cột `LONGTEXT` (`JsonText`). |
| `com.dippie.security` | Cấu hình Spring Security phi trạng thái (`SecurityConfig`), ký và xác thực JWT (`JwtService`, `JwtAuthenticationFilter`), truy xuất người dùng hiện tại (`CurrentViewer`, `SecurityContextCurrentViewer`, `Viewer`, `Role`), giới hạn tần suất đăng nhập (`LoginRateLimiter`), kiểm tra độ mạnh/giới hạn byte mật khẩu (`PasswordPolicy`) và thuộc tính cấu hình (`JwtProperties`, `RateLimitProperties`, `CorsProperties`). |
| `com.dippie.user` | Thực thể tài khoản (`User`, `UserRepository`), luồng đăng nhập/đăng ký/hồ sơ (`AuthService`, `AuthController`), kiểm tra kết nối cơ sở dữ liệu (`HealthController`), khởi tạo quản trị viên (`AdminBootstrap`, `BootstrapAdminProperties`) và tài khoản demo (`DemoAccountSeeder`). |
| `com.dippie.account` | Tự phục vụ tài khoản cho người dùng đã đăng nhập: cập nhật hồ sơ cá nhân và đổi mật khẩu kèm cấp lại JWT mới (`AccountController`, `AccountService`, `UpdateProfileRequest`, `ChangePasswordRequest`). |
| `com.dippie.admin` | Quản trị người dùng có khóa bi quan và bảo vệ quản trị viên hoạt động cuối cùng (`AdminUserController`, `AdminUserService`, `AdminUserPolicy`, `AdminUserView`, `UpdateUserRoleRequest`, `UpdateUserStatusRequest`, `AdminPageResponse`), nhật ký kiểm toán quản trị (`AdminAuditController`, `AdminAuditService`, `AdminAuditEvent`, `AdminAuditEventRepository`, `AdminAuditEventView`, `CatalogImportAuditListener`) và thống kê vận hành (`AdminStatsController`, `AdminStatsService`, `AdminStatsQuery`, `AdminStatsResponse`, `TopWatchedEpisodeView`, `TopRatedMovieView`). |
| `com.dippie.movie` | Truy vấn, lọc, sắp xếp và phân trang phim bằng SQL thông qua JPA `Specification` và Hibernate Criteria (`Movie`, `MovieRepository`, `MovieQueryService`, `MovieController`, `MovieFilter`, `MovieBrowseQuery`, `MovieBrowsePage`, `MovieVisibility`, `MovieView`, `MediaDescriptorView`, `MovieGenre`, `MovieGenreRepository`, `MovieGenreTerm`, `MovieGenreTermRepository`). |
| `com.dippie.catalog.domain` | Các thực thể JPA danh mục chuẩn hóa (`CatalogRecord`, `CatalogSeries`, `CatalogSeason`, `CatalogEpisode`, `CatalogSegment`, `CatalogScene`, `CatalogMediaAsset`, `CatalogTextTrack`, `CatalogGenre`, `CatalogCharacter`, `CatalogSource`, `CatalogAttributeScale`, `CatalogPackage`, `CatalogSnapshot`, `AuditLog`), bảng liên kết, view, repository và enum miền (`PublicationStatus`, `AccessPolicy`). |
| `com.dippie.catalog.query` | Đọc chi tiết bộ phim/tập phim, kiểm tra quyền hiển thị, tìm kiếm toàn văn và mô hình đọc danh mục bất biến hướng sự kiện (`CatalogController`, `CatalogQueryService`, `CatalogSearchService`, `CatalogVisibility`, `YoutubeSource`, `CatalogReadModel`, `CatalogReadModelCache`, `CatalogReadModelLoader`, `CatalogRecommendationRows`). Khi `CatalogImportService` phát `CatalogImportedEvent` sau commit, `CatalogReadModelCache` tải lại snapshot `CatalogReadModel` mới qua `CatalogReadModelLoader`, còn các luồng đọc đồng thời tiếp tục đọc snapshot bất biến trước đó mà không bị chặn. |
| `com.dippie.catalog.importing` | Kiểm tra hợp lệ gói JSON, tính sai khác, băm nội dung SHA-256, nhập dữ liệu nguyên tử trong một giao dịch, phát sự kiện `CatalogImportedEvent`, quản lý snapshot và xuất danh mục (`AdminCatalogController`, `CatalogImportService`, `CatalogImportTransaction`, `CatalogImportedEvent`, `CatalogPackageValidator`, `CatalogDiff`, `ContentHash`, `CatalogJsonWriter`, `CatalogRecordWriter`, `CatalogRecordMapper`, `CatalogLegacyRows`, `CatalogLinks`, `CatalogExportService`, `CatalogSnapshotService`, `CatalogIndexPublisher`, `NoopCatalogIndexPublisher`). |
| `com.dippie.library` | Quản lý danh sách xem sau, yêu thích và đánh giá phim của người dùng (`LibraryController`, `LibraryEntry`, `AbstractLibraryListService`, `WatchlistService`, `WatchlistItem`, `FavoriteService`, `FavoriteItem`, `MovieRatingService`, `MovieRating`). |
| `com.dippie.progress` | Lưu và truy vấn tiến độ xem theo tập phim, khóa lạc quan, chính sách đánh dấu hoàn thành và gợi ý tập xem tiếp theo (`WatchProgressController`, `WatchProgressService`, `WatchProgress`, `WatchProgressCommand`, `WatchDuration`, `CompletionPolicy`, `PlaybackContext`, `PlaybackContextResolver`, `NextEpisodeController`, `NextEpisodeService`, `NextEpisodePolicy`). |
| `com.dippie.media` | Ủy quyền phát video, cấp vé JWT ngắn hạn, định vị tệp đa nguồn an toàn và truyền phát HTTP Byte Range (`PlaybackGrantController`, `PlaybackGrantService`, `PlaybackAccess`, `MediaStreamController`, `MediaStreamService`, `ByteRange`, `MediaFileResolver`, `MediaLocationStrategy`, `DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`, `MediaPathPolicy`, `MediaAsset`). |
| `com.dippie.recommendation` | Gợi ý phim chạy trực tiếp trên backend Java theo chiến lược chấm điểm tương đồng từ cơ sở dữ liệu và chiến lược tìm kiếm ngữ nghĩa theo tâm trạng có dự phòng (`RecommendationController`, `RecommendationService`, `RecommendationStrategy`, `AffinityRecommendationStrategy`, `MoodRecommendationStrategy`, `CandidateCatalog`, `ViewerSignals`, `ViewerSignalsRepository`, `RecommendationCandidate`, `RecommendationContext`, `RecommendationRequest`, `RecommendationResult`, `RecommendedItem`). |
| `com.dippie.ai` | Giao tiếp HTTP với dịch vụ AI Python, chuyển tiếp SSE CineBot, truy vấn khám phá/tìm kiếm ngữ nghĩa, xuất bản chỉ mục danh mục qua HTTP và đồng bộ snapshot mới nhất khi khởi động (`AiServiceClient`, `AiClientConfig`, `AiProperties`, `ChatProxyController`, `DiscoverProxyController`, `HttpCatalogIndexPublisher`, `CatalogIndexBootstrapper`, `AiIndexConfiguration`, `AiUnavailableException`, `AiUpstreamException`, `PayloadTooLargeException`). |
| `com.dippie.web` | Phục vụ trang SPA từ `frontend/dist`, tài nguyên tĩnh `/assets/**`, `/illustrations/**` và cấu hình đặc tả OpenAPI/Swagger UI (`SpaController`, `WebResourceConfig`, `WebProperties`, `OpenApiConfig`). |

## 4. Quy tắc nghiệp vụ chính

### Lan truyền trạng thái xuất bản (Publication cascade)

- Một bản ghi danh mục chỉ được coi là đã xuất bản khi trường `status` trong `record_json` (chuyển chữ thường) bằng `ready`.
- Quyền hiển thị lan truyền từ trên xuống theo thứ bậc `series -> season -> episode -> segment / scene / asset`: với người dùng không phải `ROLE_ADMIN`, tập phim chỉ hiển thị khi chính tập đó, bộ phim cha và mùa cha (nếu có) đều ở trạng thái `ready`; phân cảnh chỉ hiển thị nếu đã xuất bản và tài nguyên `mediaAssetId` (nếu gắn) nằm trong tập tài nguyên được phép truy cập. Quản trị viên nhìn thấy toàn bộ bản ghi.
- Trên bảng `movies`, bản ghi trùng mã với một `series` hoặc `episode` chưa xuất bản cũng bị ẩn khỏi danh sách và trang chi tiết. Mọi tài nguyên bị ẩn đều trả `404` thay vì `403`.
- **Lớp triển khai**: `PublicationStatus`, `CatalogRecord`, `CatalogScene`, `CatalogVisibility`, `CatalogQueryService`, `CatalogSearchService`, `MovieVisibility`, `MovieQueryService`.

### Chính sách truy cập tài nguyên (Access policy)

- Một `CatalogMediaAsset` chỉ cho phép truy cập khi thỏa đồng thời ba điều kiện: đang bật (`enabled = true`), đã xuất bản (trừ khi người gọi là quản trị viên), và `AccessPolicy` cho phép vai trò hiện tại (`public` cho phép tất cả, `authenticated` yêu cầu đã đăng nhập, `private` chỉ cho phép quản trị viên, giá trị không xác định bị từ chối).
- Khi cấp vé phát hoặc truyền phát tệp, `PlaybackAccess` kết hợp kiểm tra cờ khả dụng/công khai trên `MediaAsset` cùng `CatalogMediaAsset.isAccessibleBy(...)`; nếu bị từ chối đều ném `NotFoundException` (`404`) để không lộ sự tồn tại của tài nguyên.
- **Lớp triển khai**: `AccessPolicy`, `CatalogMediaAsset`, `MediaAsset`, `PlaybackAccess`.

### Tiến độ xem: khóa lạc quan và ngưỡng hoàn thành

- **Khóa lạc quan**: payload gửi lên `expectedVersion` (số nguyên). Giá trị `-1` dùng để tạo mới bản ghi (`optLockVersion = 0`); giá trị `>= 0` dùng để cập nhật và phải khớp `optLockVersion` hiện tại, sau đó tăng lên `expectedVersion + 1` thông qua truy vấn cập nhật có điều kiện phiên bản (`WatchProgressRepository.advanceVersion`). Khi phiên bản sai lệch hoặc có ghi đồng thời gây trùng khóa duy nhất, hệ thống trả `409 Conflict` (`ConflictException`) với thông điệp cố định `"Watch progress changed since it was loaded; refresh and retry."`.
- **Ngữ cảnh phát hợp lệ**: tập phim và bộ/mùa cha phải đã xuất bản; `CatalogMediaAsset` phải có `contentType = video`, `role` khác `dub` và `audio_description`, `coverage_json` có mục `scope = full_episode` khớp `episodeId`, đồng thời `MediaAsset` khả dụng với MIME `video/*`.
- **Thời lượng và vị trí**: thời lượng chuẩn là giá trị dương nhỏ nhất giữa thời lượng của tập và tài nguyên (`WatchDuration.resolve`). `positionMs` phải là số nguyên trong đoạn `[0, 86_400_000]` và không vượt quá thời lượng đã biết.
- **Ngưỡng hoàn thành**: với `durationMs > 0`, tính `fivePercent = max(1, durationMs / 20)`, `nearEnd = min(60_000, fivePercent)` và `threshold = max(durationMs - fivePercent, durationMs - nearEnd)`; tập được xem là hoàn thành khi `positionMs >= threshold`. Danh sách gần đây trả tối đa 30 mục mới nhất.
- **Lớp triển khai**: `WatchProgress`, `WatchProgressCommand`, `WatchDuration`, `CompletionPolicy`, `PlaybackContext`, `PlaybackContextResolver`, `WatchProgressService`, `WatchProgressRepository`, `WatchProgressController`.

### Thư viện cá nhân lũy đẳng (Idempotent library)

- Thêm phim vào `watchlist` hoặc `favorites` kiểm tra sự tồn tại và quyền hiển thị của phim qua `MovieVisibility`. Nếu bản ghi `(user_id, movie_id)` đã có thì trả lại bản ghi hiện tại.
- Để an toàn trước tranh chấp ghi đồng thời trên ràng buộc duy nhất, lệnh lưu chạy trong giao dịch `REQUIRES_NEW`; khi bắt được `DataIntegrityViolationException`, hệ thống đọc lại bản ghi vừa được luồng khác chèn và trả về kết quả thành công.
- Đánh giá phim (`ratings`) kiểm tra điểm nguyên trong khoảng `1..10`; đánh giá lại cập nhật điểm và `updatedAt` trên cùng bản ghi, đồng thời xử lý tranh chấp chèn đồng thời theo cùng nguyên tắc.
- **Lớp triển khai**: `LibraryEntry`, `AbstractLibraryListService`, `WatchlistService`, `WatchlistItem`, `WatchlistItemRepository`, `FavoriteService`, `FavoriteItem`, `FavoriteItemRepository`, `MovieRating`, `MovieRatingService`, `MovieRatingRepository`, `LibraryController`.

### Vé phát video và truyền phát HTTP Byte Range

- `POST /api/media/grant/{assetId}` xác thực quyền qua `PlaybackAccess` rồi phát hành JWT HS256 có thời hạn mặc định 5 phút (`dippie.jwt.playback-grant-validity: PT5M`) gồm các claim `assetId`, `role`, `type=playback_grant`, `purpose=playback`. Token này bị từ chối nếu dùng làm token đăng nhập ở `JwtService.loginSubject`.
- `GET` và `HEAD /api/media/stream/{assetId}` chấp nhận `?grant=` hoặc `?token=` (ngoài phiên đăng nhập Bearer), ủy quyền lại qua `PlaybackAccess`, định vị tệp theo chuỗi chiến lược (`DriveCacheLocation` -> `DriveMountLocation` -> `LocalMediaLocation` với `drive`, hoặc chỉ `LocalMediaLocation` với `local`) và kiểm tra chống path traversal/symlink trong `MediaPathPolicy`.
- `ByteRange.parse` xử lý yêu cầu toàn tệp (`200 OK`) và các dạng `Range`: `bytes=a-b`, `bytes=a-`, `bytes=-n` (`206 Partial Content`, kèm `Accept-Ranges: bytes`, `Content-Range`, đọc theo khối 64 KB qua `FileChannel`). Header sai cú pháp hoặc không thỏa mãn trả `416 Range Not Satisfiable` với `Content-Range: bytes */<totalLength>` và thân rỗng.
- **Lớp triển khai**: `PlaybackGrantController`, `PlaybackGrantService`, `PlaybackGrantResponse`, `PlaybackAccess`, `JwtService`, `MediaStreamController`, `MediaStreamService`, `ResolvedMediaStream`, `ByteRange`, `RangeNotSatisfiableException`, `MediaFileResolver`, `MediaLocationStrategy`, `DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`, `MediaPathPolicy`.

### Nhập danh mục: băm nội dung và xung đột phiên bản

- Gói danh mục được kiểm tra cấu trúc, mã định danh, đường dẫn tệp tương đối, phạm vi `coverage`, quan hệ mùa-bộ và tham chiếu chéo giữa các tập hợp (`CatalogPackageValidator`, `CatalogDiff`).
- `ContentHash` chuẩn hóa JSON qua `CatalogJsonWriter.encode(document, true)` (sắp xếp khóa theo thứ tự mã điểm Unicode, loại bỏ khoảng trắng, giữ nguyên ký tự non-ASCII, định dạng số thực tương thích Python) rồi tính SHA-256 dạng hex chữ thường.
- Quy tắc phát hiện xung đột trong `CatalogDiff`: bản ghi chưa có -> tạo mới; `revision > storedRevision` -> cập nhật; `revision == storedRevision` và trùng `ContentHash` -> không đổi (lũy đẳng); `revision < storedRevision` hoặc cùng `revision` nhưng khác nội dung -> xung đột (`409 Conflict`).
- Quá trình nhập được tuần tự hóa bằng `ReentrantLock` trong `CatalogImportService`. Toàn bộ việc ghi gói, bản ghi danh mục, bảng legacy (`movies`, `media_assets`), bảng liên kết và bản ghi `CatalogSnapshot` (khởi tạo ở trạng thái `PENDING`) diễn ra trong **một giao dịch duy nhất** (`CatalogImportTransaction`) với khóa bi quan `PESSIMISTIC_WRITE`.
- **Phát sự kiện và xuất bản chỉ mục sau khi nhập**: sau khi giao dịch commit thành công và có thay đổi (`!commit.unchanged()`), `CatalogImportService` phát sự kiện `CatalogImportedEvent` qua `ApplicationEventPublisher` (để `CatalogReadModelCache` làm mới mô hình đọc và `CatalogImportAuditListener` ghi nhật ký kiểm toán), sau đó gọi tường minh `CatalogSnapshotService.synchronize(...)` để xuất dữ liệu qua `CatalogExportService` và đẩy sang dịch vụ AI qua `CatalogIndexPublisher`. Khi cấu hình `dippie.ai.base-url`, `AiIndexConfiguration` đăng ký `HttpCatalogIndexPublisher` gửi yêu cầu `POST /api/index/sync` (kèm `X-Service-Token`) qua `AiServiceClient` và ánh xạ phản hồi bằng `IndexSyncOutcome.fromAcknowledgment`; nếu không có cấu hình AI, `CatalogIndexPublisherConfiguration` cung cấp `NoopCatalogIndexPublisher` làm cài đặt dự phòng (trả `SYNC_FAILED`). Kết quả (`READY`, `PENDING`, `BUILDING`, `BLOCKED`, `SYNC_FAILED`) được lưu lại qua `CatalogSnapshot.completeIndexSync(...)`.
- **Đồng bộ snapshot mới nhất khi khởi động**: `CatalogIndexBootstrapper` lắng nghe `ApplicationReadyEvent`, khởi chạy virtual thread (`catalog-index-bootstrap`) thăm dò `AiServiceClient.isReadyForIndexSync(...)` trên `/ready` (tối đa 30 giây, mỗi 500 ms). Ngay khi dịch vụ AI sẵn sàng (HTTP `200`, hoặc HTTP `503` báo `database = "ready"` và chưa có chỉ mục được chấp nhận), hệ thống gọi `CatalogImportService.publishCurrentIndex()` -> `CatalogSnapshotService.publishCurrent()` để xuất bản snapshot mới nhất lên chỉ mục AI.
- **Đối soát snapshot khi liệt kê**: `CatalogSnapshotService.list()` (chạy trong khóa `ReentrantLock` của `CatalogImportService`) đọc danh sách snapshot theo `snapshotVersion` giảm dần, chuẩn hóa trạng thái cũ `CACHE_REQUIRED` về `BLOCKED`, và đối soát mọi bản ghi đang chờ chỉ mục (`CatalogSnapshot.isAwaitingIndex()`, gồm `PENDING` và `BUILDING`): snapshot mới nhất được kiểm tra/xuất bản lại với chỉ mục AI và cập nhật qua `completeIndexSync(...)`, còn các snapshot cũ hơn bị thay thế bằng `CatalogSnapshot.supersede(...)` để chuyển sang `SYNC_FAILED`.
- **Lớp triển khai**: `AdminCatalogController`, `CatalogImportService`, `CatalogImportTransaction`, `CatalogImportedEvent`, `CatalogPackageValidator`, `CatalogDiff`, `ContentHash`, `CatalogJsonWriter`, `CatalogRecordWriter`, `CatalogRecordMapper`, `CatalogLegacyRows`, `CatalogLinks`, `CatalogExportService`, `CatalogSnapshotService`, `CatalogSnapshot`, `CatalogPackage`, `CatalogIndexPublisher`, `HttpCatalogIndexPublisher`, `CatalogIndexBootstrapper`, `AiIndexConfiguration`, `CatalogIndexPublisherConfiguration`, `NoopCatalogIndexPublisher`, `IndexSyncOutcome`.

### Công thức chấm điểm gợi ý (Recommendation scoring)

- Endpoint `POST /api/recommend` được triển khai trực tiếp bằng Java (`RecommendationController`, `RecommendationService`, `RecommendationStrategy`, `AffinityRecommendationStrategy`, `MoodRecommendationStrategy`, `CandidateCatalog`, `ViewerSignals`, `ViewerSignalsRepository`, `RecommendedItem`) và vận hành trực tiếp trên cơ sở dữ liệu ngay cả khi dịch vụ AI Python không hoạt động.
- **Lựa chọn chiến lược và cơ chế dự phòng**: khi yêu cầu không truyền `mood` (hoặc chuỗi rỗng), `RecommendationService` chọn `AffinityRecommendationStrategy`. Khi yêu cầu có `mood`, hệ thống dùng `MoodRecommendationStrategy` để gọi tìm kiếm ngữ nghĩa `/v1/search` trên dịch vụ AI (với `query = mood`, `limit = 100` và `constraints.include_genres` từ các thể loại đầu vào), đối chiếu kết quả theo thứ tự trả về với tập ứng viên hợp lệ chưa xem và ánh xạ sang `RecommendedItem` (kèm `score` và `evidence`). Nếu dịch vụ AI không khả dụng (`AiUnavailableException`), trả lỗi upstream (`AiUpstreamException`) hoặc phản hồi không hợp lệ, `MoodRecommendationStrategy` tự động dự phòng (fallback) sang `AffinityRecommendationStrategy`.
- **Quy tắc ứng viên hợp lệ (`CandidateCatalog`, `CatalogReadModel`, `RecommendationContext`)**:
  - `CandidateCatalog` đọc danh sách ứng viên dựng sẵn từ `CatalogReadModelCache` (tải qua `CatalogRecommendationRows`). Chỉ xét các tập phim `CatalogEpisode` có `publicationStatus = 'ready'` thỏa mãn `CatalogVisibility.followsPublishedParents` (tập phim, `CatalogSeries` cha và `CatalogSeason` cha nếu có đều đã xuất bản).
  - Tập phải có ít nhất một `CatalogMediaAsset` công khai (`isAccessibleBy(false, false)` — đang bật, đã xuất bản, `accessPolicy = public`), là video tập phim (`isEpisodeVideo()` — `contentType = video`, `role` khác `dub` và `audio_description`) với `coverage_json` chứa mục `scope = full_episode` cho tập đó; hoặc tập chưa có khai báo phạm vi phủ sóng ở bất kỳ `CatalogMediaAsset` nào và không có `primary_media_asset_id` trong `rawJson`.
  - Loại bỏ mọi ứng viên khớp tập `seen` (theo `episode.id` hoặc `seriesId`). Nếu yêu cầu có chỉ định thể loại (`genres`), chỉ giữ các ứng viên giao không rỗng với tập thể loại đó sau chuẩn hóa case-folding Unicode (thể loại của tập kế thừa thể loại của bộ phim cha nếu tập không gắn trực tiếp).
- **Quy tắc tín hiệu `liked` và `seen` (`ViewerSignalsRepository`, `ViewerSignals`)**:
  - `liked` gồm `likedMovieIds` trong yêu cầu; nếu người dùng đã đăng nhập, bổ sung tối đa 100 mục mới nhất cho mỗi nhóm: phim yêu thích (`FavoriteItem`), phim đánh giá cao (`MovieRating` với `rating >= 8`) và tập đã bắt đầu xem (`WatchProgress` với `positionMs > 0`).
  - `seen` gồm toàn bộ `liked` (bao gồm `likedMovieIds` đầu vào) cộng toàn bộ bản ghi `FavoriteItem`, toàn bộ `MovieRating` (mọi mức điểm) và toàn bộ `WatchProgress` (mọi giá trị `positionMs`) của người dùng đó không giới hạn 100 mục.
- Công thức chấm điểm tương đồng trong `AffinityRecommendationStrategy` cho mỗi ứng viên hợp lệ chưa nằm trong `seen` (tập sở thích `preferred`, `themes`, `chars` và `series` được tổng hợp từ thể loại đầu vào cùng các mục trong danh mục khớp `liked`):

```text
score = rating * 0.1
      + 2.0 * |genres(item) ∩ preferred| / max(1, |preferred|)
      + 1.5 * |(themes ∪ keywords)(item) ∩ themes| / max(1, |themes|)
      + 1.2 * |characters(item) ∩ chars| / max(1, |chars|)
      + (0.8 nếu series_id thuộc tập series đã thích, ngược lại 0)
```

- Điểm được làm tròn theo quy tắc `HALF_EVEN` tới 4 chữ số thập phân (`BigDecimal.setScale(4, RoundingMode.HALF_EVEN)`) để khớp hành vi hàm `round(..., 4)` của Python. Danh sách ứng viên sắp xếp giảm dần theo `score`, tiếp đến giảm dần theo `rating` và tăng dần theo `id`, lấy tối đa `limit` mục (mặc định `4`, trong đoạn `1..100`) và đóng gói thành `RecommendedItem`.
- **Lớp triển khai**: `RecommendationController`, `RecommendationService`, `RecommendationStrategy`, `AffinityRecommendationStrategy`, `MoodRecommendationStrategy`, `CandidateCatalog`, `ViewerSignals`, `ViewerSignalsRepository`, `RecommendationCandidate`, `RecommendationContext`, `RecommendationRequest`, `RecommendationResult`, `RecommendedItem`.

### Mô hình đọc danh mục hướng sự kiện (Catalog read model)

- `CatalogReadModel` là snapshot bất biến trong bộ nhớ (mọi tập hợp đều khóa bằng `List.copyOf`/`Set.copyOf`/`Map.copyOf`) gom sẵn danh sách series, tập mã series/tập phim hiển thị, ánh xạ mùa đã xuất bản, tập `hiddenMovieIds`, các dòng tìm kiếm (`CatalogSearchService.SearchRow`), tập ứng viên gợi ý (`CatalogRecommendationRows`) và danh sách tập đã xuất bản (`CatalogReadModel.SeriesEpisode`) theo từng series.
- `CatalogReadModelLoader.load()` chạy trong giao dịch mới chỉ đọc với mức cô lập `REPEATABLE_READ` (`@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)`) để dựng snapshot nhất quán.
- `CatalogReadModelCache` giữ tham chiếu hiện hành trong `AtomicReference<CatalogReadModel>`, khởi tạo lười ở lần gọi `current()` đầu tiên và lắng nghe `CatalogImportedEvent` (`@EventListener` trên `refresh`) do `CatalogImportService` phát sau khi `CatalogImportTransaction` commit thành công.
- Trong lúc `refresh` nạp snapshot mới, các luồng đọc đồng thời qua `current()` không bị chặn và tiếp tục đọc snapshot bất biến trước đó cho đến khi `AtomicReference.set(...)` hoán đổi nguyên tử. Nếu `refresh` gặp lỗi runtime, `invalidate()` đặt tham chiếu về `null` để lần đọc kế tiếp tự nạp lại. Mô hình này phục vụ chung cho `CatalogQueryService`, `CatalogSearchService`, `CatalogVisibility`, `CandidateCatalog` và `NextEpisodeService`.
- **Lớp triển khai**: `CatalogReadModel`, `CatalogReadModelCache`, `CatalogReadModelLoader`, `CatalogRecommendationRows`, `CatalogImportedEvent`, `CatalogImportService`.

### Duyệt và lọc phim bằng SQL với JPA Specification

- `MovieRepository` mở rộng `JpaSpecificationExecutor<Movie>` để đếm và lọc phim trực tiếp trong SQL thay vì nạp toàn bộ bảng `movies` lên bộ nhớ.
- `MovieFilter.specification(yearMin, yearMax, hiddenIds)` tạo `Specification<Movie>` kết hợp lọc khoảng năm phát hành (`releaseYear`), loại trừ các mã phim bị ẩn theo `MovieVisibility.hiddenMovieIds()` (lấy từ `CatalogReadModelCache`), tìm chuỗi con không phân biệt hoa thường qua hàm `regexp_like` trên `title`/`description`, và lọc thể loại bằng truy vấn con `EXISTS` trên bảng chuẩn hóa `MovieGenre`/`MovieGenreTerm` (chỉ dự phòng sang cột CSV `genres` khi phim chưa có bản ghi liên kết thể loại).
- `MovieQueryService` thực thi `movieRepository.count(filters)` cùng truy vấn Hibernate Criteria (`orderedMovies(...)`) có `offset`/`fetch` ngay trên SQL trong một `TransactionTemplate` chỉ đọc; khi sắp xếp theo `title` và khóa phụ `id`, biểu thức ép sang `byte[]` để giữ đúng thứ tự mã điểm UTF-8 tương thích bản Python.
- **Lớp triển khai**: `MovieQueryService`, `MovieRepository`, `MovieFilter`, `MovieBrowseQuery`, `MovieBrowsePage`, `MovieVisibility`, `MovieGenre`, `MovieGenreTerm`.

### Phiên bản hóa token và khóa tài khoản (Token versioning)

- Mọi JWT đăng nhập đều mang claim số nguyên không âm `ver` phản ánh `User.tokenVersion` (mặc định `0`), được gắn trong `JwtService.issueLoginToken(...)` và bắt buộc kiểm tra hợp lệ trong `JwtService.parseLoginToken(...)`.
- Khi người dùng đổi mật khẩu (`AccountService.changePassword` -> `User.changePassword`), hoặc quản trị viên đổi vai trò (`User.changeRole`) hay khóa/mở khóa tài khoản (`User.disable`, `User.enable`), `tokenVersion` tăng lên `1`, thu hồi tức thì mọi JWT đã phát hành trước đó. Riêng `POST /api/users/me/password` trả về `AuthResponse` kèm JWT mới đã nâng `ver`.
- `JwtAuthenticationFilter` chỉ thiết lập xác thực khi `user.isEnabled()` là `true` và `user.getTokenVersion() == loginToken.tokenVersion()`; token lệch phiên bản hoặc thuộc tài khoản bị khóa bị bỏ qua (xử lý như ẩn danh, dẫn tới `401` trên endpoint yêu cầu đăng nhập).
- Tại `POST /api/auth/login`, sau khi xác minh đúng mật khẩu, nếu `user.isEnabled()` là `false`, `AuthService.login` ném `ForbiddenException` trả `403 Forbidden` (`"Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên."`); sai mật khẩu vẫn trả `401` chung để không lộ trạng thái tài khoản.
- **Lớp triển khai**: `User`, `JwtService`, `JwtAuthenticationFilter`, `AuthService`, `AccountService`, `AccountController`.

### Quản trị người dùng: chống tự sửa và bảo vệ quản trị viên cuối cùng

- Các endpoint `/api/admin/users`, `/api/admin/users/{id}/role` và `/api/admin/users/{id}/status` yêu cầu `ROLE_ADMIN` (`CurrentViewer.requireAdmin()`). Danh sách hỗ trợ tìm kiếm theo `username` hoặc `email` và phân trang (`page >= 0`, `1 <= size <= 100`, sắp xếp theo `id` tăng dần).
- **Chống tự thay đổi**: `AdminUserPolicy` kiểm tra `actor.userId()` khác `target.getId()`; nếu quản trị viên tự đổi vai trò hoặc trạng thái của chính mình thì trả `409 Conflict` (`"Không thể tự thay đổi quyền hoặc trạng thái của chính mình."`).
- **Bảo vệ quản trị viên hoạt động cuối cùng bằng khóa bi quan theo thứ tự `id`**: trong giao dịch của `AdminUserService.changeRole` và `changeStatus`, hệ thống gọi `UserRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN)` để lấy khóa `PESSIMISTIC_WRITE` trên toàn bộ quản trị viên đang hoạt động theo thứ tự `ORDER BY u.id ASC` (ngăn deadlock khi nhiều quản trị viên thao tác chéo đồng thời), rồi mới khóa dòng mục tiêu qua `UserRepository.findByIdForUpdate(targetUserId)` nếu mục tiêu chưa nằm trong tập đã khóa. Nếu thao tác hạ quyền (`ROLE_ADMIN` -> `ROLE_USER`) hoặc khóa (`enabled = false`) một quản trị viên đang hoạt động mà `enabledAdminCount <= 1`, `AdminUserPolicy` ném `409 Conflict` (`"Hệ thống phải còn ít nhất một quản trị viên đang hoạt động."`).
- **Lớp triển khai**: `AdminUserController`, `AdminUserService`, `AdminUserPolicy`, `AdminUserView`, `UpdateUserRoleRequest`, `UpdateUserStatusRequest`, `AdminPageResponse`, `UserRepository`.

### Nhật ký kiểm toán quản trị (Admin audit trail)

- `AdminAuditService` lưu vết các thao tác quản trị vào bảng `admin_audit_events` (`AdminAuditEvent`, `AdminAuditEventRepository`) gồm `actorUserId`, `actorUsername` (mặc định `"system"` nếu rỗng), `action`, `targetType`, `targetId`, chi tiết JSON (`details_json`) và `createdAt` (UTC).
- Các hành động được ghi nhận gồm:
  - `USER_ROLE_CHANGED`, `USER_DISABLED`, `USER_ENABLED` (đối tượng `USER`): ghi trực tiếp trong giao dịch của `AdminUserService` kèm giá trị trước và sau thay đổi.
  - `CATALOG_IMPORTED` (đối tượng `CATALOG_PACKAGE`): ghi tự động qua `CatalogImportAuditListener` (`@EventListener`) khi nhận `CatalogImportedEvent`, lưu `packageId`, `revision`, `snapshotVersion`, `snapshotId` và `importedAt`.
- `AdminAuditController` cung cấp `GET /api/admin/audit-events` (yêu cầu `ROLE_ADMIN`, phân trang `page >= 0`, `1 <= size <= 100`) sắp xếp theo `createdAt` giảm dần, tiếp đến `id` giảm dần.
- **Lớp triển khai**: `AdminAuditController`, `AdminAuditService`, `AdminAuditEvent`, `AdminAuditEventRepository`, `AdminAuditEventView`, `CatalogImportAuditListener`, `CatalogImportedEvent`.

### Thống kê quản trị (Admin statistics)

- `AdminStatsController` (`GET /api/admin/stats`, yêu cầu `ROLE_ADMIN`) gọi `AdminStatsService` và `AdminStatsQuery` để tổng hợp: `totalUsers`, `activeUsers`, `newUsersLast7Days` (7 ngày gần nhất theo `Clock`), `totalMovies`, `publishedEpisodes` (`publicationStatus = 'ready'`), `totalWatchSessions`, `completedWatchSessions`, `completionRate` (chia và làm tròn `RoundingMode.HALF_UP` tới 4 chữ số thập phân, trả `0.0` khi chưa có phiên xem), `topWatchedEpisodes` (top 10 theo số người xem riêng biệt giảm dần) và `topRatedMovies` (top 10 theo điểm trung bình giảm dần, tiếp đến số lượt đánh giá giảm dần).
- **Tương đương ngưỡng hoàn thành với `CompletionPolicy`**: `AdminStatsQuery.countCompletedWatchSessions()` tính thời lượng hiệu dụng ngay trong JPQL là giá trị dương nhỏ hơn giữa `CatalogEpisode.durationMs` và `CatalogMediaAsset.durationMs` (khớp `WatchDuration.resolve`), sau đó áp dụng đúng ba nhánh tương đương hoàn toàn với `CompletionPolicy`: thời lượng `>= 1_200_000 ms` kiểm tra `positionMs >= duration - 60_000`; từ `20 ms` đến dưới `1_200_000 ms` kiểm tra `positionMs * 20 >= duration * 19`; dưới `20 ms` kiểm tra `positionMs >= duration - 1`.
- **Lớp triển khai**: `AdminStatsController`, `AdminStatsService`, `AdminStatsQuery`, `AdminStatsResponse`, `TopWatchedEpisodeView`, `TopRatedMovieView`, `CompletionPolicy`, `WatchDuration`.

### Tập xem tiếp theo (Next episode)

- `NextEpisodePolicy` chuyển đổi quy tắc của thủ tục lưu trữ `sp_next_episode` sang logic miền Java thuần (chạy đồng nhất trên cả H2 và MySQL), đồng thời dùng ngưỡng hoàn thành chuẩn từ `WatchDuration` và `CompletionPolicy` thay vì chỉ so sánh `position_ms < duration_ms`.
- `NextEpisodeService` yêu cầu người dùng đã đăng nhập (`CurrentViewer.require()`), kiểm tra `seriesId` thuộc `CatalogReadModel.visibleSeriesIds()` (ném `NotFoundException` trả `404` nếu không tồn tại hoặc chưa xuất bản), lấy mọi tập đã xuất bản và hiển thị của bộ phim từ `CatalogReadModel.seriesEpisodes(seriesId)` giống `sp_next_episode` (kể cả tập chỉ phát qua YouTube), chỉ loại các tập mà tệp video duy nhất là `private` đối với người không phải quản trị viên (`memberAccessible`), và truy vấn tiến độ theo bộ phim qua `WatchProgressRepository.findForSeries`.
- Trong `NextEpisodePolicy.pick`, mọi tập đã đạt ngưỡng hoàn thành (`WatchDuration.isCompletedAt`) bị loại bỏ; các tập còn lại có `positionMs > 0` mang lý do `RESUME` (kèm `updatedAt`), còn tập chưa xem hoặc `positionMs == 0` mang lý do `NEXT_UNWATCHED`. Thuật toán ưu tiên các tập `RESUME` theo `updatedAt` giảm dần (xem dở gần nhất), sau đó mới xét các tập `NEXT_UNWATCHED` theo thứ tự `(seasonNumber, episodeNumber, id)` tăng dần (giá trị `null` xếp sau). Khi không còn tập nào để xem tiếp, `NextEpisodeController` (`GET /api/users/next-episode?seriesId=...`) trả `data: null` cùng thông điệp `"Bạn đã xem hết bộ phim này."`.
- **Lớp triển khai**: `NextEpisodeController`, `NextEpisodeService`, `NextEpisodePolicy`, `WatchDuration`, `CompletionPolicy`, `CatalogReadModelCache`, `WatchProgressRepository`.

## 5. Design pattern và nguyên tắc OOP

### Các Design pattern áp dụng

| Pattern | Lớp triển khai | Lý do áp dụng |
| --- | --- | --- |
| Strategy | `MediaLocationStrategy`, `DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`, `MediaFileResolver`; `CatalogIndexPublisher`, `HttpCatalogIndexPublisher`, `NoopCatalogIndexPublisher`, `AiIndexConfiguration`, `CatalogIndexPublisherConfiguration` | Tách từng cách định vị tệp video (cache Drive, ổ gắn Drive, thư mục cục bộ) và chiến lược xuất bản chỉ mục AI (`HttpCatalogIndexPublisher` khi cấu hình `dippie.ai.base-url`, dự phòng `NoopCatalogIndexPublisher` khi chạy không có dịch vụ AI) thành cài đặt độc lập, dễ mở rộng mà không tạo nhánh `if/else` phức tạp trong service. |
| Strategy | `RecommendationStrategy`, `AffinityRecommendationStrategy`, `MoodRecommendationStrategy`, `RecommendationService` | Tách chiến lược gợi ý theo độ tương đồng từ cơ sở dữ liệu và chiến lược tìm kiếm ngữ nghĩa theo tâm trạng (kèm cơ chế dự phòng sang chấm điểm tương đồng khi dịch vụ AI không khả dụng) thành các cài đặt hoán đổi được. |
| Template Method | `AbstractLibraryListService`, `WatchlistService`, `FavoriteService` (cùng thực thể cơ sở `LibraryEntry`) | Dùng chung toàn bộ khung nghiệp vụ của danh sách cá nhân (xác thực người gọi, kiểm tra hiển thị phim, thêm lũy đẳng và khôi phục khi trùng khóa duy nhất trong giao dịch `REQUIRES_NEW`), lớp con chỉ cài đặt thao tác lưu trữ đặc thù. |
| Value Object | `ByteRange`, `ContentHash`, `WatchDuration`, `WatchProgressCommand`, `PasswordPolicy`, `PublicationStatus`, `AccessPolicy`, `Viewer`, `MovieBrowseQuery`, `YoutubeSource` | Biểu diễn khái niệm miền bất biến bằng Java `record` và `enum`, tự kiểm tra hợp lệ khi khởi tạo và đóng gói trực tiếp các phép tính gắn với dữ liệu đó. |
| Facade | `AiServiceClient` | Che giấu chi tiết gọi `HttpClient`, cấu hình timeout, gắn header `X-Service-Token`, tuần tự hóa JSON và ánh xạ lỗi mạng sang `AiUnavailableException`/`AiUpstreamException` khỏi `ChatProxyController`, `DiscoverProxyController`, `MoodRecommendationStrategy`, `HttpCatalogIndexPublisher` và `CatalogIndexBootstrapper`. |
| Repository | `UserRepository`, `MovieRepository` (mở rộng `JpaSpecificationExecutor`), `WatchProgressRepository`, `MediaAssetRepository`, `WatchlistItemRepository`, `FavoriteItemRepository`, `MovieRatingRepository`, `AdminAuditEventRepository`, `AdminStatsQuery`, các `Catalog*Repository`, cùng `CatalogDiff`, `CatalogRecordWriter`, `CatalogImportTransaction` | Cô lập tầng truy cập dữ liệu JPA/Hibernate (tương thích cả H2 và MySQL 8) khỏi logic điều phối ứng dụng và quy tắc miền. |
| Domain Event / Observer | `CatalogImportedEvent` (phát bởi `CatalogImportService` qua `ApplicationEventPublisher`) với hai `@EventListener`: `CatalogReadModelCache` và `CatalogImportAuditListener`; `CatalogIndexBootstrapper` (`@EventListener(ApplicationReadyEvent.class)`); `AdminBootstrap`, `DemoAccountSeeder` (`ApplicationRunner`); `CatalogRecord`, `CatalogPackage`, `CatalogSnapshot`, `MediaAsset` (`@PrePersist`/`@PreUpdate`) | Sau khi giao dịch `CatalogImportTransaction` commit thành công và có thay đổi, `CatalogImportService` phát `CatalogImportedEvent` để tách rời hai phản ứng độc lập: `CatalogReadModelCache` tải lại snapshot mô hình đọc bất biến và `CatalogImportAuditListener` ghi sự kiện `CATALOG_IMPORTED` qua `AdminAuditService`. Riêng đồng bộ chỉ mục AI (`CatalogSnapshotService.synchronize`) vẫn gọi tường minh sau đó trong khóa `ReentrantLock`. Ngoài ra, `CatalogIndexBootstrapper` lắng nghe `ApplicationReadyEvent` để đồng bộ snapshot mới nhất khi khởi động, kết hợp `ApplicationRunner` và JPA `@PrePersist`/`@PreUpdate` ở mức vòng đời framework. |

### Đóng gói và nguyên tắc SOLID

- **Đóng gói (Encapsulation)**: không dùng Lombok; các thực thể JPA không mở setter công khai tùy ý cho các thuộc tính bất biến mà chỉ cung cấp constructor rõ ràng và phương thức miền thể hiện ý định như `WatchProgress.start(...)`, `WatchProgress.advance(...)`, `MovieRating.updateRating(...)`, `User.changePassword(...)`, `User.updateProfile(...)`, `User.changeRole(...)`, `User.disable(...)`, `User.enable(...)`, `CatalogMediaAsset.isAccessibleBy(...)`, `CatalogPackage.applyImport(...)`, `CatalogSnapshot.completeIndexSync(...)`, `CatalogSnapshot.supersede(...)`. DTO phản hồi và yêu cầu đều là Java `record` bất biến.
- **Single Responsibility (SRP)**: tách rành mạch Controller (ánh xạ HTTP), Service (điều phối giao dịch và quy trình), đối tượng chính sách/miền (`CompletionPolicy`, `NextEpisodePolicy`, `AdminUserPolicy`, `PlaybackAccess`, `MediaPathPolicy`, `CatalogPackageValidator`, `MovieFilter`) và Repository.
- **Open/Closed (OCP)**: thêm nguồn lưu trữ media chỉ cần tạo cài đặt mới cho `MediaLocationStrategy`; thêm thuật toán gợi ý chỉ cần cài đặt `RecommendationStrategy`; phần xuất bản chỉ mục AI kích hoạt `HttpCatalogIndexPublisher` qua `AiIndexConfiguration` khi có `dippie.ai.base-url`, tự động ghi đè `NoopCatalogIndexPublisher` dự phòng đăng ký bằng `@ConditionalOnMissingBean` trong `CatalogIndexPublisherConfiguration`.
- **Liskov Substitution (LSP)**: `WatchlistService` và `FavoriteService` thay thế hoàn toàn hợp đồng của `AbstractLibraryListService`; mọi ngoại lệ kế thừa `DomainException` đều được `GlobalExceptionHandler` xử lý đồng nhất thành `ApiResponse`.
- **Interface Segregation (ISP)**: các giao diện được giữ nhỏ và chuyên biệt (`CurrentViewer`, `MediaLocationStrategy`, `CatalogIndexPublisher`, `RecommendationStrategy`), kết hợp các projection hẹp trên repository để chỉ đọc đúng cột cần thiết.
- **Dependency Inversion (DIP)**: toàn bộ bean sử dụng constructor injection với trường `final`, phụ thuộc vào abstraction (`CurrentViewer`, `PasswordEncoder`, `Clock`, `CatalogIndexPublisher`, `MediaLocationStrategy`, `RecommendationStrategy`) giúp kiểm thử đơn vị độc lập với thời gian hệ thống và hạ tầng ngoài.

## 6. Cách chạy

### Yêu cầu hệ thống

- **JDK 21** trở lên.
- **Node.js 22** trở lên (cần để build giao diện `frontend/dist` lần đầu hoặc khi chỉnh sửa frontend).
- **Python 3.11** (tùy chọn — chỉ cần khi chạy kèm dịch vụ AI cho CineBot và tìm kiếm ngữ nghĩa).

### Chạy hồ sơ `demo` (mặc định, dùng H2)

Từ thư mục gốc của kho mã nguồn (Windows PowerShell):

```powershell
.\start-java.ps1
```

Script tự kiểm tra và build `frontend/dist` nếu chưa tồn tại, sau đó khởi động Spring Boot với hồ sơ `demo` tại `http://localhost:3000`. Nếu giao diện đã được build sẵn, có thể chạy trực tiếp từ `backend/springboot`:

```powershell
.\mvnw.cmd spring-boot:run
```

(Trên Linux/macOS dùng `./mvnw spring-boot:run`.)

Đặc điểm hồ sơ `demo`:

- Cơ sở dữ liệu H2 lưu tệp tại `target/dippie-java/dippie` (có thể đổi bằng `DIPPIE_DATA_DIR`), `spring.jpa.hibernate.ddl-auto=update`.
- Nếu bỏ trống `APP_JWT_SECRET`, `JwtService` tự sinh khóa tạm 32 byte khi khởi động.
- `DemoAccountSeeder` tự tạo hai tài khoản mẫu (chỉ dùng cho môi trường thử nghiệm):
  - Quản trị viên: `admin` / `password123` (`ROLE_ADMIN`)
  - Người dùng thường: `user` / `password123` (`ROLE_USER`)
- Nạp gói danh mục qua màn hình quản trị để thêm phim và tài nguyên media vào cơ sở dữ liệu mới.
- Sau khi khởi động, tài liệu tương tác Swagger UI sẵn sàng tại `http://localhost:3000/swagger-ui.html` (chuyển hướng sang `/swagger-ui/index.html`) và đặc tả OpenAPI JSON tại `http://localhost:3000/v3/api-docs` (cấu hình trong `OpenApiConfig`).

### Chạy hồ sơ `mysql` (MySQL 8)

Hồ sơ `mysql` bật `spring.jpa.hibernate.ddl-auto=validate` và tắt seed dữ liệu demo (`dippie.seed-demo-data=false`), do đó với cơ sở dữ liệu mới cần tạo lược đồ MySQL trước bằng tệp `database/schema/01_init.sql`, sau đó nạp `database/objects/views.sql`, `functions.sql`, `procedures.sql`, `triggers.sql` (xem `database/README.md`). Với cơ sở dữ liệu MySQL đã tồn tại từ trước, cần chạy bổ sung migration `database/migrations/11_account_controls_and_admin_audit.sql` (lũy đẳng: thêm cột `users.enabled`, `users.token_version` và tạo bảng `admin_audit_events`). Toàn bộ entity đã được Hibernate đối chiếu thành công với lược đồ này trên MySQL 8.0.46; các luồng nhập danh mục, thư viện cá nhân, tiến độ xem (kèm trigger thời lượng) và gợi ý đã chạy thử trên MySQL.

Đặt các biến môi trường cần thiết trước khi chạy:

```powershell
$env:MYSQL_HOST = "localhost"
$env:MYSQL_PORT = "3306"
$env:MYSQL_DB = "movie_rcm_db"
$env:MYSQL_USER = "dippie_app"
$env:MYSQL_PASSWORD = "<mat-khau-mysql>"

# Bắt buộc ngoài hồ sơ demo/test: chuỗi Base64 giải mã tối thiểu 32 byte
$env:APP_JWT_SECRET = "<base64-32-bytes-tro-len>"

# Tùy chọn để AdminBootstrap tạo tài khoản quản trị đầu tiên (mật khẩu 16 ký tự..72 byte UTF-8)
$env:APP_BOOTSTRAP_ADMIN_USERNAME = "dippie-admin"
$env:APP_BOOTSTRAP_ADMIN_EMAIL = "admin@example.com"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "<mat-khau-quan-tri-tu-16-ky-tu>"

.\start-java.ps1 -Profile mysql
```

Các biến môi trường tùy chọn khác được khai báo trong `src/main/resources/application.yml`: `PORT` (mặc định `3000`), `APP_JWT_EXPIRATION` (mặc định `PT24H`), `APP_MEDIA_ROOT`, `APP_MEDIA_CACHE_ROOT`, `APP_MEDIA_DRIVE_MOUNT`, `AI_SERVICE_URL` (mặc định `http://127.0.0.1:8000`), `CATALOG_SYNC_TOKEN`, `DIPPIE_WEB_DIST` (mặc định `../../frontend/dist`), `CORS_ALLOWED_ORIGINS`.

### Chạy kèm dịch vụ AI Python

```powershell
.\start-java.ps1 -WithAi
```

(Có thể kết hợp với MySQL bằng `.\start-java.ps1 -Profile mysql -WithAi`.)

Script sẽ sinh ngẫu nhiên `CATALOG_SYNC_TOKEN`, khởi động FastAPI ở `127.0.0.1:8000` với `DIPPIE_AI_ONLY=true`, SQLite cục bộ `target/dippie-java/ai/ai.sqlite3` và trạng thái chỉ mục `target/dippie-java/ai/catalog-sync.json` (tách khỏi trạng thái của bản Python đầy đủ trong `data/indices/`), rồi khởi động Spring Boot ở cổng `3000`. Nếu `APP_JWT_SECRET` chưa đặt, script sinh khóa ngẫu nhiên cho tiến trình AI. Khi dừng tiến trình Java, script tự dừng tiến trình Python con.

Để làm lại dữ liệu demo từ đầu, dừng ứng dụng rồi xóa thư mục `target/dippie-java`; thao tác này xóa đồng thời cơ sở dữ liệu H2 và trạng thái chỉ mục AI nên hai bên luôn khớp generation.

### Nhập danh mục mẫu từ `data/catalog-by-series`

1. Đăng nhập tài khoản quản trị (`admin` / `password123` ở hồ sơ `demo`, hoặc tài khoản bootstrap ở hồ sơ `mysql`).
2. Mở trang quản trị nhập dữ liệu tại `http://localhost:3000/admin/import`.
3. Chọn lần lượt các tệp trong thư mục `data/catalog-by-series/`, bấm kiểm tra hợp lệ và nhập vào hệ thống:
   - `catalog.jadoo.json`
   - `catalog.larva.json`
   - `catalog.ori.json`
   - `catalog.doraemon.json`
   - `catalog.trang_quynh.json`
   - `catalog.pokemon.json`
   - `catalog.mr_bean_animated.json`
   - `catalog.peppa_pig.json`

## 7. Kiểm thử

Chạy toàn bộ bộ kiểm thử từ thư mục `backend/springboot`:

```bash
./mvnw test
```

Trên Windows PowerShell dùng `.\mvnw.cmd test` (có thể thêm `-o -B` khi chạy offline/CI). Kiểm thử chạy trên hồ sơ `test` với H2 in-memory, bao gồm unit test cho quy tắc miền và integration test bằng `@SpringBootTest` + `@AutoConfigureMockMvc`.

Thống kê số lượng lớp kiểm thử theo package (tổng cộng 46 lớp):

| Package | Số lớp kiểm thử |
| --- | ---: |
| `com.dippie.account` | 1 |
| `com.dippie.admin` | 3 |
| `com.dippie.ai` | 4 |
| `com.dippie.catalog.domain` | 5 |
| `com.dippie.catalog.importing` | 6 |
| `com.dippie.catalog.query` | 2 |
| `com.dippie.library` | 1 |
| `com.dippie.media` | 4 |
| `com.dippie.movie` | 4 |
| `com.dippie.progress` | 5 |
| `com.dippie.recommendation` | 2 |
| `com.dippie.security` | 5 |
| `com.dippie.user` | 2 |
| `com.dippie.web` | 2 |
| **Tổng cộng** | **46** |

## 8. Khác biệt có chủ đích so với bản Python

1. **Lỗi kiểm tra dữ liệu trả `400` theo envelope thay vì `422`**: bản Python (FastAPI/Pydantic) trả HTTP `422 Unprocessable Entity` với thân `{"detail": [...]}` cho lỗi kiểu dữ liệu hoặc thiếu trường. Bản Java xử lý tập trung qua `GlobalExceptionHandler` và trả HTTP `400 Bad Request` trong khung `ApiResponse` (`success: false`, `message`, `data: null`, `timestamp`). Ngoại lệ theo đúng hợp đồng là `POST /api/admin/catalog/validate`, vẫn trả JSON thô `CatalogValidation` mã `400`.
2. **Kiểm tra xác thực và phân quyền trước khi kiểm tra payload**: các endpoint người dùng (`WatchProgressService`, `AbstractLibraryListService`, `MovieRatingService`, `AuthService`) gọi `CurrentViewer.require()` (`401`) và các endpoint quản trị (`AdminCatalogController`) gọi `CurrentViewer.requireAdmin()` (`403`) trước khi kiểm tra hợp lệ các trường nghiệp vụ (và tại `AdminCatalogController`, trước cả bước đọc thân JSON từ `HttpServletRequest`). Nhờ đó, yêu cầu chưa đăng nhập hoặc thiếu quyền không nhận nhầm lỗi kiểm tra dữ liệu `400`/`422`.
3. **Tiến độ xem bắt buộc số nguyên JSON nghiêm ngặt**: `WatchProgressCommand.from` kiểm tra chặt `isIntegralNumber() && canConvertToLong()` cho cả `expectedVersion` và `positionMs`, từ chối các giá trị boolean (`true`/`false`), chuỗi số (`"1"`, `"100"`) và số thực kể cả dạng `.0` (`1.0`, `12.5`) với mã `400`, thay vì ép kiểu ngầm.
4. **Envelope `503` đặc thù phía Java khi không kết nối được dịch vụ AI**: khi dịch vụ AI Python không hoạt động, bị từ chối kết nối, quá hạn chờ hoặc trả dữ liệu không đọc được, `AiServiceClient` ném `AiUnavailableException` (`ServiceUnavailableException`), được `GlobalExceptionHandler` trả về HTTP `503 Service Unavailable` theo khung chuẩn `ApiResponse`: `{"success": false, "message": "Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.", "data": null, "timestamp": "..."}` trên `/api/chat`, `/v1/discover` và `/v1/search`.
