# Danh mục phim

## Catalog theo từng bộ

Các gói trong `catalog-by-series/` viết theo schema `dippie.catalog.v1` ([JSON Schema](../docs/contracts/animation-catalog-v1/dippie.catalog.v1.schema.json)). Mỗi tệp chứa đủ thông tin của bộ phim, các mùa, tập, nhân vật và nguồn tham chiếu. Phần `segments` lưu tên, tóm tắt, từ khóa và khoảng thời gian của từng đoạn. Khi dựng kho mới, bạn nhập thẳng 6 gói này.

| Catalog | Tập | Tập có phụ đề | Phân đoạn mới | Nguồn phát |
| --- | ---: | ---: | ---: | --- |
| [Xin Chào Jadoo](catalog-by-series/catalog.jadoo.json) | 99 | 98 | 276 | Tệp cục bộ hoặc Google Drive |
| [Larva](catalog-by-series/catalog.larva.json) | 104 | 83 | 0 | [Playlist Larva TUBA](https://www.youtube.com/playlist?list=PLxrv_vTPuZ6Qi6Hx868jli1m5CDCEDfxj) |
| [Công chúa Ori](catalog-by-series/catalog.ori.json) | 180 | 131 | 363 | [Playlist MYKINGDOM / The Child](https://www.youtube.com/playlist?list=PLMtVgyZo_WBIoqfvWtlg9_F7J1Xz53bB4) |
| [Doraemon](catalog-by-series/catalog.doraemon.json) | 337 | 310 | 887 | Các playlist POPS Kids (mùa 5-7, 9-12) |
| [Trạng Quỳnh Thời Nhí Nhố](catalog-by-series/catalog.trang_quynh.json) | 35 | 35 | 51 | [Playlist gốc](https://www.youtube.com/playlist?list=PLJscQ6PJLmu3zd6RfLDVUxP4EfahF2OKO) và [kênh phát hành](https://www.youtube.com/channel/UCndGTJ1ZllgoBXRUR-xfDUQ) |
| [Heo Peppa](catalog-by-series/catalog.peppa_pig.json) | 51 | 51 | 100 | [Peppa Pig Pretend Play](https://www.youtube.com/channel/UCoCSjc-4JCGqOxuhuw-xjSw), [TVmaze](https://www.tvmaze.com/shows/4972/peppa-pig/episodeguide) |
| **Tổng** | **806** | **708** | **1.677** | |

## Chất lượng dữ liệu và nguồn tham chiếu

- Đợt rà ngày 23/11/2026 bổ sung 1.677 phân đoạn cho 610 tập, nâng tổng số tập có mốc phân đoạn lên 617 và có 1.237/1.372 tập qua hết bộ kiểm metadata cấp tập.
- Phân đoạn mới rút ra từ lời thoại đã đối chiếu hai lượt. Mốc đầu và mốc cuối lấy từ phụ đề của đúng video, lưu kèm ngôn ngữ, đường dẫn và mã SHA-256 trong `sources` và `text_tracks`.
- Mỗi bản ghi giữ số tập, vị trí trong playlist, thời lượng và nguồn (`youtube_upload_date` khi có). Bộ Ori có 136 tập phát qua trình phát nhúng và 44 tập mở sang liên kết gốc.
- Ngày 25/11/2026 mình bỏ hẳn hai bộ Pokémon và Mr Bean hoạt hình vì toàn bộ 566 tập bị YouTube chặn tại Việt Nam (chặn vùng, "Video không có sẵn" hoặc chặn nhúng). Các bộ còn lại giữ nguyên mã, mùa và số tập; 15 tập Jadoo bị gỡ trên YouTube vẫn phát từ Google Drive.
- Heo Peppa lấy cách đánh mùa và số tập theo TVmaze. Phần dữ liệu chuyển thể từ TVmaze tuân theo giấy phép CC BY-SA của nguồn ([Giấy phép API TVmaze](https://www.tvmaze.com/api#licensing)).

## Kiểm tra và nhập vào Dippie

Đăng nhập tài khoản quản trị rồi tải từng tệp trong `data/catalog-by-series/` ở trang `/admin/import`, hoặc chạy script PowerShell bên dưới (script gọi `/api/admin/catalog/validate` trước rồi mới nhập qua `/api/admin/catalog/import`):

```powershell
.\scripts\import_preview_catalog.ps1 `
    -CatalogPath 'data/catalog-by-series/catalog.jadoo.json' `
    -ApiBaseUrl 'http://127.0.0.1:3000/api'
```
