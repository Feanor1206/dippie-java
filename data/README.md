# Danh mục phim

## Catalog theo từng bộ

Các gói trong `catalog-by-series/` tuân theo schema `dippie.catalog.v1` ([JSON Schema](../docs/contracts/animation-catalog-v1/dippie.catalog.v1.schema.json)). Mỗi tệp tự chứa bộ phim, các mùa, tập, nhân vật và nguồn tham chiếu. Mục `segments` lưu tên, tóm tắt, từ khóa và khoảng thời gian của từng đoạn. Nhập trực tiếp tám gói này khi tạo kho mới.

| Catalog | Tập | Tập có phụ đề | Phân đoạn mới | Nguồn phát |
| --- | ---: | ---: | ---: | --- |
| [Xin Chào Jadoo](catalog-by-series/catalog.jadoo.json) | 99 | 98 | 276 | Tệp cục bộ hoặc Google Drive |
| [Larva](catalog-by-series/catalog.larva.json) | 104 | 83 | 0 | [Playlist Larva TUBA](https://www.youtube.com/playlist?list=PLxrv_vTPuZ6Qi6Hx868jli1m5CDCEDfxj) |
| [Công chúa Ori](catalog-by-series/catalog.ori.json) | 180 | 131 | 363 | [Playlist MYKINGDOM / The Child](https://www.youtube.com/playlist?list=PLMtVgyZo_WBIoqfvWtlg9_F7J1Xz53bB4) |
| [Doraemon](catalog-by-series/catalog.doraemon.json) | 337 | 310 | 887 | Các playlist POPS Kids (mùa 5–7, 9–12) |
| [Trạng Quỳnh Thời Nhí Nhố](catalog-by-series/catalog.trang_quynh.json) | 35 | 35 | 51 | [Playlist gốc](https://www.youtube.com/playlist?list=PLJscQ6PJLmu3zd6RfLDVUxP4EfahF2OKO) và [kênh phát hành](https://www.youtube.com/channel/UCndGTJ1ZllgoBXRUR-xfDUQ) |
| [Pokémon](catalog-by-series/catalog.pokemon.json) | 479 | 0 | 0 | [Pokémon TV](https://www.youtube.com/channel/UC8wrbl5viYtQmRUgdFdf4ew), [TVmaze](https://www.tvmaze.com/shows/590/pokemon/episodeguide) |
| [Mr Bean hoạt hình](catalog-by-series/catalog.mr_bean_animated.json) | 87 | 0 | 0 | [Mr Bean](https://www.youtube.com/channel/UCkAGrHCLFmlK3H2kd6isipg), [TVmaze](https://www.tvmaze.com/shows/5251/mr-bean/episodeguide) |
| [Heo Peppa](catalog-by-series/catalog.peppa_pig.json) | 51 | 51 | 100 | [Peppa Pig Pretend Play](https://www.youtube.com/channel/UCoCSjc-4JCGqOxuhuw-xjSw), [TVmaze](https://www.tvmaze.com/shows/4972/peppa-pig/episodeguide) |
| **Tổng** | **1.372** | **708** | **1.677** | |

## Chất lượng dữ liệu và nguồn tham chiếu

- Đợt rà ngày 23/11/2026 bổ sung 1.677 phân đoạn cho 610 tập (tổng cộng 617 tập có mốc phân đoạn và 1.237/1.372 tập đạt đầy đủ bộ kiểm metadata cấp tập).
- Phân đoạn mới trích từ lời thoại đã đối chiếu hai lượt; mốc đầu/cuối lấy từ phụ đề của đúng video và lưu kèm ngôn ngữ, đường dẫn và mã SHA-256 trong `sources` / `text_tracks`.
- Bản ghi lưu số tập, vị trí trong playlist, thời lượng và nguồn (`youtube_upload_date` khi có). Ori có 136 tập dùng trình phát nhúng và 44 tập mở qua liên kết gốc.
- Pokémon và Mr Bean giữ mùa và số tập trên kênh phát hành; Heo Peppa dùng mùa và số tập của TVmaze. Phần dữ liệu chuyển thể từ TVmaze tuân theo giấy phép CC BY-SA của nguồn ([Giấy phép API TVmaze](https://www.tvmaze.com/api#licensing)).

## Kiểm tra và nhập vào Dippie

Đăng nhập tài khoản quản trị và tải từng tệp trong `data/catalog-by-series/` tại trang `/admin/import`, hoặc dùng script PowerShell (tự gọi `/api/admin/catalog/validate` trước khi nhập qua `/api/admin/catalog/import`):

```powershell
.\scripts\import_preview_catalog.ps1 `
    -CatalogPath 'data/catalog-by-series/catalog.jadoo.json' `
    -ApiBaseUrl 'http://127.0.0.1:3000/api'
```
