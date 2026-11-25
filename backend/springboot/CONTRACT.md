# HTTP Contract (frozen)

This document defines the HTTP contract served by the Dippie Spring Boot backend. Field names, envelopes vs raw bodies,
status codes, and Vietnamese messages are frozen because the React frontend depends on them.

## 1. Response bodies

### 1.1 Envelope

Most endpoints answer with `com.dippie.common.web.ApiResponse`:

```json
{"success": true, "message": "Thành công", "data": {}, "timestamp": "2026-11-25T08:52:00.123456"}
```

Errors always use the envelope with `success:false`, `data:null`, produced by throwing a `DomainException`.

### 1.2 Raw JSON (no envelope) on success

| Endpoint | Body |
| --- | --- |
| `GET /api/catalog/series` | array of series dicts |
| `GET /api/catalog/series/cards` | array of series cards |
| `GET /api/catalog/series/{id}` | `{"series":…, "seasons":[…], "episodes":[…]}` |
| `GET /api/catalog/episodes/{id}` | `{"episode":…, "segments":[…], "mediaAssets":[…], "scenes":[…], "youtube":…, "youtubeFallback":…}` |
| `GET /api/catalog/search` | array of hits |
| `POST /api/media/grant/{assetId}` | grant object |
| `POST /api/admin/catalog/validate` | validation result (also the 400 body) |
| `POST /api/admin/catalog/import` | import result |
| `GET /api/admin/catalog/history` | array of packages |
| `GET /api/admin/catalog/snapshots` | array of snapshots |
| `POST /api/admin/catalog/snapshots/{id}/retry` | snapshot |
| `GET /api/admin/catalog/export` | catalog package document |
| `POST /api/recommend` | `{"recommendedMovies":[…], "reasoning":"…"}` |
| `POST /v1/search`, `POST /v1/discover` | `{"structuredIntent":…, "explanation":…, "results":[…], "followUps":[…], "pipelineStages":…}` |

### 1.3 Field naming

Jackson must emit exactly the keys of the Python dicts. Most are camelCase, **but some are snake_case and must stay
so**, e.g. movie items carry `item_type`, `series_id`, `episode_id`, `episode_number`; recommendation items follow
`schemas.MovieItem` (mixed). Use `@JsonProperty` on record components where the key is not camelCase. Null fields are
emitted as `null` (never omitted).

Timestamps are UTC ISO-8601 local date-times without offset (`LocalDateTime`), as Python `isoformat()` did.

## 2. Authentication & authorization

- `Authorization: Bearer <jwt>`; HS256; claims `sub` (username, ≤50 chars), `iat`, `exp`; default lifetime 24h.
  Secret = Base64 of ≥32 bytes from `dippie.jwt.secret` (`APP_JWT_SECRET`). In `demo` profile, when no secret is
  configured, generate a random 32-byte key at startup and log a warning. Outside demo, a missing/short secret must
  fail startup.
- A token with claim `type=playback_grant` or `purpose=playback` is **not** a login token.
- An invalid/expired token is treated as anonymous (never 401 by itself).
- User endpoints (`/api/users/**`, `/api/auth/me`) without a valid user → **401** `"Chưa xác thực người dùng"`.
- Admin endpoints (`/api/admin/**`) without ROLE_ADMIN (including anonymous) → **403**
  `"Quyền truy cập bị từ chối (Yêu cầu ROLE_ADMIN)"`.
- Roles: `ROLE_USER`, `ROLE_ADMIN`.
- Passwords: BCrypt; a password longer than 72 UTF-8 bytes is rejected (register 400, login 401 generic).
- Login/register rate limit: 30 attempts per 60s per `(clientIp, identifier.casefold())`, LRU of 4096 clients;
  exceeded → **429** `"Bạn đã thử đăng nhập quá nhiều lần. Vui lòng chờ một phút."` + `Retry-After` header.

## 3. Endpoint list (all must exist)

| Method | Path | Owner package |
| --- | --- | --- |
| GET | `/api/health` | user (or common) |
| POST | `/api/auth/login`, `/api/auth/register` · GET `/api/auth/me` | user |
| GET | `/api/movies`, `/api/movies/browse`, `/api/movies/{id}`, `/api/movies/{id}/media-descriptor` | movie |
| GET | `/api/catalog/series`, `/api/catalog/series/cards`, `/api/catalog/series/{id}`, `/api/catalog/episodes/{id}`, `/api/catalog/search` | catalog.query |
| GET/POST/DELETE | `/api/users/watchlist[/{movieId}]`, `/api/users/favorites[/{movieId}]`, `/api/users/ratings[/{movieId}]` | library |
| GET/PUT/DELETE | `/api/users/watch-progress[/{episodeId}]` | progress |
| POST | `/api/media/grant/{assetId}` · GET/HEAD `/api/media/stream/{assetId}` | media |
| POST/GET | `/api/admin/catalog/{validate,import,history,snapshots,snapshots/{id}/retry,export}` | catalog.importing |
| POST | `/api/recommend` | recommendation |
| POST | `/api/chat` (SSE: `metadata`, `token`, `done`, `error`) | assistant.chat |
| POST | `/v1/search`, `/v1/discover` (raw JSON) | assistant.search |
| GET | `/api/assistant/status` | assistant |
| GET | `/`, `/search`, `/profile`, `/admin/import`, `/movies/{id}`, `/series/{id}`, `/assets/**`, `/illustrations/**` | web |

`/api/chat` (`LocalChatController`), `/v1/search`, and `/v1/discover` (`LocalDiscoverController`) are served directly by Java with raw JSON / SSE responses and validation detail payloads.

## 4. Key business rules (must be modelled in domain code)

- **Publication**: a catalog record is published iff its `record_json` parses to an object whose `status`
  lower-cased is `ready` (see `catalog_publication_status` / `is_published`). Visibility cascades
  series → season → episode → segment/scene/asset; admins see everything.
- **Movie visibility** (`_visible_movies`): a movie row whose id is a catalog series id is hidden unless that series is
  published; a movie whose id is a catalog episode id is hidden unless the episode, its series and its season (if any)
  are published.
- **Asset access policy** (`_asset_allowed`): disabled → deny; non-admin & unpublished → deny; `public` → allow;
  anonymous → deny; `authenticated` → allow; `private` → admin only; unknown → deny.
- **Hidden resources answer 404, never 403** (media, episodes, progress).
- **Watch progress**: `expectedVersion` integer; `-1` creates, otherwise must equal current version → else **409**
  `"Watch progress changed since it was loaded; refresh and retry."`; position integer 0..86 400 000 ms and not beyond
  duration; duration = min of known positive episode/asset durations; completed when
  `position >= max(dur - max(1, dur/20), dur - min(60000, max(1, dur/20)))`. Playable context requires a
  `video` asset (role not `dub`/`audio_description`) whose `coverage_json` has a `full_episode` entry for the episode
  and a playable `media_assets` row with a `video/*` mime type. Recent list: 30 newest.
- **Library add is idempotent** and survives the unique-constraint race (catch `DataIntegrityViolationException`,
  re-read the existing row). Rating must be integer 1..10; re-rating updates the row.
- **Playback grant**: JWT with `assetId`, `role`, `type=playback_grant`, `purpose=playback`, valid 300s. Streaming
  accepts `?grant=` or `?token=`; supports `HEAD`, full `200`, `Range: bytes=a-b`, `bytes=a-`, `bytes=-n` → `206`,
  malformed/unsatisfiable → `416` with `Content-Range: bytes */<len>` and empty body.
- **Catalog import**: validate structure, compute SHA-256 of canonical JSON (sorted keys, no spaces, UTF-8, non-ASCII
  kept), detect revision/hash conflicts → 409, write everything in **one transaction**, guard concurrent imports with a
  lock, create a `catalog_snapshots` row, then publish the eligible catalog to the AI index.

## 5. Endpoints added beyond the Python version

`GET /api/catalog/episodes/{id}` keeps its raw JSON response and adds two fields:

- Each `mediaAssets` item has `available` (`boolean`). It is `true` when a corresponding `media_assets` row exists and the streaming file resolver can currently locate its file. A missing row or file returns `false`.
- `youtubeFallback` is `{"videoId": "<id>", "canEmbed": true}` when `youtube` is `null` and the episode has a valid `youtube_video_id` in `external_ids`. IDs must be 11 characters from `A–Z`, `a–z`, `0–9`, `_` or `-`. Otherwise `youtubeFallback` is `null`. The existing `youtube` field keeps its playback and watch rules.

Endpoints in the following table wrap their success payload in the standard `ApiResponse` envelope (`{"success": true, "message": "...", "data": ..., "timestamp": "..."}`) and return `ApiResponse` errors with `success: false`, `data: null`.

| Method | Path | Auth requirement | Controller | Response `data` shape |
| --- | --- | --- | --- | --- |
| `PUT` | `/api/users/me` | Authenticated user (`401` if anonymous) | `com.dippie.account.AccountController` | `UserProfileResponse`: `{"id": long, "username": string, "email": string, "fullName": string, "role": string}` (`message`: `"Cập nhật thông tin tài khoản thành công"`). Request body (`UpdateProfileRequest`): `{"fullName": string\|null, "email": string}`. |
| `POST` | `/api/users/me/password` | Authenticated user (`401` if anonymous) | `com.dippie.account.AccountController` | `AuthResponse`: `{"token": string, "type": "Bearer", "id": long, "username": string, "email": string, "fullName": string, "role": string}` (`message`: `"Đổi mật khẩu thành công"`). Request body (`ChangePasswordRequest`): `{"currentPassword": string, "newPassword": string}`. Increments `tokenVersion` (revoking prior tokens) and issues a new JWT. |
| `GET` | `/api/admin/users` | `ROLE_ADMIN` (`403` otherwise) | `com.dippie.admin.AdminUserController` | `AdminPageResponse<AdminUserView>`: `{"items": [{"id": long, "username": string, "email": string, "fullName": string, "role": string, "enabled": boolean, "createdAt": LocalDateTime}], "page": int, "size": int, "totalElements": long, "totalPages": int, "hasNext": boolean, "hasPrevious": boolean}`. Query params: `query` (default `""`), `page` (default `0`), `size` (default `20`, max `100`). |
| `PATCH` | `/api/admin/users/{id}/role` | `ROLE_ADMIN` (`403` otherwise) | `com.dippie.admin.AdminUserController` | `AdminUserView`: `{"id": long, "username": string, "email": string, "fullName": string, "role": string, "enabled": boolean, "createdAt": LocalDateTime}` (`message`: `"Cập nhật vai trò người dùng thành công"`). Request body (`UpdateUserRoleRequest`): `{"role": "ROLE_USER" \| "ROLE_ADMIN"}`. Returns `409` on self-change or demoting the last active admin, `404` if user not found, `400` on invalid role. |
| `PATCH` | `/api/admin/users/{id}/status` | `ROLE_ADMIN` (`403` otherwise) | `com.dippie.admin.AdminUserController` | `AdminUserView`: `{"id": long, "username": string, "email": string, "fullName": string, "role": string, "enabled": boolean, "createdAt": LocalDateTime}` (`message`: `"Cập nhật trạng thái người dùng thành công"`). Request body (`UpdateUserStatusRequest`): `{"enabled": boolean}`. Returns `409` on self-change or disabling the last active admin, `404` if user not found, `400` if `enabled` is null. |
| `GET` | `/api/admin/audit-events` | `ROLE_ADMIN` (`403` otherwise) | `com.dippie.admin.AdminAuditController` | `AdminPageResponse<AdminAuditEventView>`: `{"items": [{"id": long, "actorUsername": string, "action": string, "targetType": string, "targetId": string, "details": object, "createdAt": LocalDateTime}], "page": int, "size": int, "totalElements": long, "totalPages": int, "hasNext": boolean, "hasPrevious": boolean}`. Query params: `page` (default `0`), `size` (default `20`, max `100`). Ordered by `createdAt` desc, `id` desc. |
| `GET` | `/api/admin/stats` | `ROLE_ADMIN` (`403` otherwise) | `com.dippie.admin.AdminStatsController` | `AdminStatsResponse`: `{"totalUsers": long, "activeUsers": long, "totalMovies": long, "publishedEpisodes": long, "totalWatchSessions": long, "completedWatchSessions": long, "completionRate": double, "topWatchedEpisodes": [{"episodeId": string, "title": string, "viewers": long}], "topRatedMovies": [{"movieId": string, "title": string, "averageRating": double, "ratingCount": long}], "newUsersLast7Days": long}`. |
| `GET` | `/api/users/next-episode` | Authenticated user (`401` if anonymous) | `com.dippie.progress.NextEpisodeController` | `NextEpisodeService.NextEpisodeView`: `{"seriesId": string, "episodeId": string, "episodeTitle": string, "seasonNumber": int\|null, "episodeNumber": int\|null, "resumePositionMs": long, "reason": "RESUME" \| "NEXT_UNWATCHED"}`, or `data: null` with `message: "Bạn đã xem hết bộ phim này."` when every published episode of the series is completed. Required query param: `seriesId` (`404` if series is missing or unpublished). |
| `GET` | `/api/assistant/status` | Public | `com.dippie.assistant.AssistantStatusController` | `AssistantStatusController.AssistantStatus`: `{"searchReady": boolean, "documents": int, "llmProvider": string}` (standard `ApiResponse` envelope with `message: "Thành công"`). |

### 5.1 Series cards

`GET /api/catalog/series/cards` is public and returns a raw JSON array. All viewers, including admins, receive
published series only, in ordinal title order. Each element has the following fields.

| Field | Type |
| --- | --- |
| `id`, `title` | string |
| `originalTitle`, `synopsis`, `posterUrl`, `backdropUrl` | string or null |
| `releaseYearStart`, `releaseYearEnd` | integer or null |
| `genres` | array of `{ "id": string, "label": string }` |
| `seasonCount`, `episodeCount` | integer |

Counts include visible published episodes and their published seasons. Genres follow the series order, omit unknown
IDs, and use the Vietnamese label, the first available label, or the ID. `posterUrl` uses the first available poster
artwork, thumbnail artwork, or `hqdefault.jpg` from the first visible episode with a valid YouTube ID, ordered by
season and episode number. `backdropUrl` uses backdrop artwork, banner artwork, `maxresdefault.jpg` from the same
YouTube video, or `posterUrl`. Catalog artwork URLs must use HTTP or HTTPS.

### 5.2 Additional fields on existing responses

- `/api/movies`, `/api/movies/browse`, and `/api/movies/{id}` include `series_title` in the catalog placement for
  series and episodes. Series and episode items fill null `poster` and `backdrop` from series card artwork and
  episode YouTube `hqdefault.jpg` and `maxresdefault.jpg`, respectively.
- `/api/recommend` fills null `poster` and `backdrop` using the same artwork rules.
- `GET /api/users/watch-progress` and `GET` or `PUT /api/users/watch-progress/{id}` include `thumbnailUrl` on each
  progress item. Its value is the episode's YouTube `hqdefault.jpg`, the series card's `posterUrl`, or `null`.
