# Dippie Java Backend — Architecture & Engineering Rules

Spring Boot 3.5 · Java 21 · JPA/Hibernate · Spring Security (stateless JWT) · H2 (demo/test) · MySQL 8 (production).

This module is the standalone Java backend for Dippie, serving both the core business API and the runtime AI
assistant in a single JVM process: CineBot SSE (`/api/chat`), lexical catalog search/discovery (`/v1/search`,
`/v1/discover`), mood and affinity recommendations (`/api/recommend`), and `/api/assistant/status`. The React frontend
(`frontend/`) is served directly by this application; `ai/` remains the offline ML pipeline.

## Hard rules (every contributor, no exceptions)

1. **No comments anywhere in code.** No `//`, no `/* */`, no Javadoc. Intent is expressed with names, small methods,
   value objects and enums. Reviews reject any file with a comment.
2. **The HTTP contract in `CONTRACT.md` is frozen.** JSON field names, envelope vs raw body, status codes and
   Vietnamese messages must match the Python implementation byte-for-byte in meaning. The frontend depends on it.
3. **Business rules live in domain objects**, not in controllers and not as if-chains in services.
   Controllers only map HTTP ⇄ application service calls. Services orchestrate; entities/value objects decide.
4. **No Lombok.** DTOs are Java `record`s. Entities use explicit constructors and intention-revealing methods
   (`progress.advanceTo(...)`, `asset.isAccessibleBy(viewer)`), no public setters for invariants.
5. **Constructor injection only**, `final` fields, no field `@Autowired`.
6. **Errors are thrown as `com.dippie.common.error.*Exception`** and rendered by `GlobalExceptionHandler` into the
   standard envelope. Never build error `ResponseEntity`s by hand in controllers unless the contract demands a raw
   error body (catalog validate, media 416).
7. **Database portability:** code must run on H2 (`MODE=MySQL`) and MySQL 8 with the schema in
   `database/schema/01_init.sql`. No native MySQL-only functions (`JSON_EXTRACT`, `HEX`) in queries. JSON columns are
   `LONGTEXT` mapped to `String`; parse them with Jackson in Java.
8. **Time** is UTC `LocalDateTime` from the injected `java.time.Clock`. Never call `LocalDateTime.now()` without a clock
   in domain/service code.
9. **Tests:** every feature package ships JUnit 5 tests. Domain rules get plain unit tests; HTTP behaviour gets
   `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` tests asserting status codes and JSON.
10. Build must pass: `./mvnw -o -B test` from `backend/springboot`.

## Package layout (package-by-feature)

```
com.dippie
├── DippieApplication
├── common
│   ├── error        DomainException hierarchy + GlobalExceptionHandler
│   ├── web          ApiResponse envelope, ClockConfig
│   ├── json         JsonText helper (parse LONGTEXT JSON safely)
│   └── text         UnicodeText (Python-compatible case-folding & whitespace stripping)
├── security         SecurityConfig, JwtService, JwtAuthenticationFilter, Viewer, CurrentViewer, Role,
│                    LoginRateLimiter, PasswordPolicy
├── user             User entity, UserRepository, AuthService, AuthController, HealthController, AdminBootstrap,
│                    DemoAccountSeeder
├── account          AccountController, AccountService (profile update, password change + token version bump)
├── admin            AdminUserController, AdminUserService, AdminUserPolicy, AdminAuditController, AdminAuditService,
│                    AdminStatsController, AdminStatsService, AdminStatsQuery
├── movie            Movie entity, MovieRepository, MovieVisibility, MovieQueryService, MovieController, MovieFilter
├── catalog
│   ├── domain       Catalog* entities, PublicationStatus, AccessPolicy, repositories
│   ├── query        CatalogQueryService, CatalogSearchService, CatalogController, CatalogReadModelCache
│   └── importing    CatalogPackageValidator, CatalogDiff, CatalogImportService, CatalogExportService,
│                    AdminCatalogController, CatalogSnapshotService, CatalogIndexPublisher
├── library          Watchlist / Favorite / Rating (AbstractLibraryListService, shared LibraryEntry base)
├── progress         WatchProgress entity, CompletionPolicy, WatchProgressService, NextEpisodePolicy, controllers
├── media            MediaAsset entity, PlaybackGrantService, ByteRange, MediaFileResolver (Strategy),
│                    MediaStreamController
├── recommendation   RecommendationService + AffinityRecommendationStrategy, MoodRecommendationStrategy (via CatalogSearch)
├── assistant        AssistantProperties (dippie.assistant.*), AssistantStatusController (GET /api/assistant/status)
│   ├── search       CatalogSearch (LocalCatalogSearch), LexicalIndex, SearchCatalogFlattener,
│   │                LocalDiscoverController (/v1/search, /v1/discover), LocalIndexPublisher
│   └── chat         IntentParser, ChatService, AnswerGenerator (TemplateAnswerGenerator, DeepSeekAnswerGenerator),
│                    LocalChatController (/api/chat SSE), CatalogReplies, MoodEvidence, ChatConfiguration
└── web              SpaController (serves frontend/dist), WebResourceConfig, OpenApiConfig
```

Cross-package rule: a package may depend on `common`, `security` and on another feature's **domain/repository**
types, never on another feature's controller.

## Design patterns used deliberately

| Pattern | Where |
| --- | --- |
| Strategy | `MediaLocationStrategy` (`DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`) via `MediaFileResolver`; `RecommendationStrategy` (`AffinityRecommendationStrategy`, `MoodRecommendationStrategy`); `CatalogIndexPublisher` (`LocalIndexPublisher`); `CatalogSearch` (`LocalCatalogSearch`); `AnswerGenerator` (`TemplateAnswerGenerator` / `DeepSeekAnswerGenerator`) |
| Value Object | `ByteRange`, `WatchDuration`, `WatchProgressCommand`, `PublicationStatus`, `AccessPolicy`, `ContentHash`, `ChatIntent`, `SearchConstraints` |
| Template Method | `AbstractLibraryListService` (`WatchlistService`, `FavoriteService`) |
| Optimistic locking | `WatchProgress` (`expectedVersion` protocol), catalog `opt_lock_version` |
| Event listener | `CatalogImportedEvent` (`CatalogReadModelCache`, `CatalogImportAuditListener`, `LocalCatalogSearch`) |

## Configuration

`application.yml` defines `dippie.*` properties bound to `@ConfigurationProperties` records in each feature
(`JwtProperties`, `MediaProperties`, `AssistantProperties`, ...). Profiles: `demo` (default, H2 file
DB, seeds demo accounts), `mysql` (validate schema), `test` (H2 in-memory, used by tests).
