# Dippie Java backend architecture and engineering rules

Built with Spring Boot 3.5, Java 21, JPA/Hibernate, stateless JWT via Spring Security, H2 for demo and tests, and MySQL 8 for production.

This module is the standalone Java backend for Dippie. It runs both the core business API and the runtime AI assistant in one JVM process: CineBot SSE (`/api/chat`), lexical catalog search and discovery (`/v1/search`, `/v1/discover`), mood and affinity recommendations (`/api/recommend`), and `/api/assistant/status`. It also serves the built React frontend from `frontend/`. The `ai/` directory remains the offline ML pipeline.

## Hard rules (every contributor, no exceptions)

1. No comments anywhere in code: no `//`, no `/* */`, and no Javadoc. Express intent through names, small methods, value objects, and enums. Reviews reject any file with a comment.
2. The HTTP contract in `CONTRACT.md` is frozen. JSON field names, envelope vs raw bodies, status codes, and Vietnamese messages must match the Python version in meaning because the frontend depends on them.
3. Business rules live in domain objects, not in controllers and not as long `if` chains in services. Controllers only map HTTP requests to application service calls; services orchestrate while entities and value objects decide.
4. No Lombok. Use Java `record` types for DTOs. Entities use explicit constructors and intention-revealing methods (like `progress.advanceTo(...)` or `asset.isAccessibleBy(viewer)`), with no public setters for invariants.
5. Constructor injection only, with `final` fields and no field `@Autowired`.
6. Throw errors as `com.dippie.common.error.*Exception` and let `GlobalExceptionHandler` render them into the standard envelope. Do not build error `ResponseEntity` objects by hand in controllers unless the contract requires a raw error body (catalog validate and media 416).
7. Database portability: code must run on both H2 (`MODE=MySQL`) and MySQL 8 against `database/schema/01_init.sql`. Avoid MySQL-only functions (`JSON_EXTRACT`, `HEX`) in queries. JSON columns are `LONGTEXT` mapped to `String`, parsed with Jackson in Java.
8. Time is UTC `LocalDateTime` from the injected `java.time.Clock`. Do not call `LocalDateTime.now()` directly in domain or service code.
9. Every feature package ships JUnit 5 tests. Domain rules get plain unit tests; HTTP behaviour gets `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` tests checking status codes and JSON.
10. The build must pass with `./mvnw -o -B test` from `backend/springboot`.

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

Cross-package rule: a package may depend on `common`, `security`, and another feature's domain or repository types, never on another feature's controller.

## Design patterns used

| Pattern | Where |
| --- | --- |
| Strategy | `MediaLocationStrategy` (`DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`) via `MediaFileResolver`; `RecommendationStrategy` (`AffinityRecommendationStrategy`, `MoodRecommendationStrategy`); `CatalogIndexPublisher` (`LocalIndexPublisher`); `CatalogSearch` (`LocalCatalogSearch`); `AnswerGenerator` (`TemplateAnswerGenerator` / `DeepSeekAnswerGenerator`) |
| Value Object | `ByteRange`, `WatchDuration`, `WatchProgressCommand`, `PublicationStatus`, `AccessPolicy`, `ContentHash`, `ChatIntent`, `SearchConstraints` |
| Template Method | `AbstractLibraryListService` (`WatchlistService`, `FavoriteService`) |
| Optimistic locking | `WatchProgress` (`expectedVersion` protocol), catalog `opt_lock_version` |
| Event listener | `CatalogImportedEvent` (`CatalogReadModelCache`, `CatalogImportAuditListener`, `LocalCatalogSearch`) |

## Configuration

`application.yml` defines `dippie.*` properties bound to `@ConfigurationProperties` records in each feature (`JwtProperties`, `MediaProperties`, `AssistantProperties`, and so on). Profiles: `demo` (default, file-backed H2 database, seeds demo accounts), `mysql` (validates schema), and `test` (in-memory H2 for tests).
