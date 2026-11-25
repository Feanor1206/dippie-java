# Dippie Java Backend — Architecture & Engineering Rules

Spring Boot 3.5 · Java 21 · JPA/Hibernate · Spring Security (stateless JWT) · H2 (demo/test) · MySQL 8 (production).

This module replaces the business backend that lived in `backend/fastapi/routers/core_api.py` and
`backend/fastapi/services/database.py`. The React frontend (`frontend/`) is unchanged and is served by this
application. AI features (CineBot chat, semantic search, frame search, embeddings) stay in the Python service
`backend/fastapi`, which this application calls over HTTP.

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
│   └── json         JsonText helper (parse LONGTEXT JSON safely)
├── security         SecurityConfig, JwtService, JwtAuthenticationFilter, Viewer, CurrentViewer, Role,
│                    LoginRateLimiter, PasswordPolicy
├── user             User entity, UserRepository, AuthService, AuthController, AdminBootstrap
├── movie            Movie entity, MovieRepository, MovieVisibility, MovieQueryService, MovieController
├── catalog
│   ├── domain       Catalog* entities, PublicationStatus, AccessPolicy, repositories
│   ├── query        CatalogQueryService, CatalogSearchService, CatalogController
│   └── importing    CatalogPackageValidator, CatalogDiff, CatalogImportService, CatalogExportService,
│                    AdminCatalogController, snapshot lifecycle
├── library          Watchlist / Favorite / Rating (shared LibraryEntry base)
├── progress         WatchProgress entity, CompletionPolicy, WatchProgressService, controller
├── media            MediaAsset entity, PlaybackGrantService, ByteRange, MediaFileResolver (Strategy),
│                    MediaStreamController
├── recommendation   RecommendationService + scoring strategies (affinity, mood via AI)
├── ai               AiServiceClient, ChatProxyController (SSE pass-through), CatalogIndexPublisher
└── web              SpaController (serves frontend/dist), CORS
```

Cross-package rule: a package may depend on `common`, `security` and on another feature's **domain/repository**
types, never on another feature's controller.

## Design patterns used deliberately

| Pattern | Where |
| --- | --- |
| Strategy | `MediaFileResolver` (local / drive cache / drive mount), `RecommendationStrategy` (affinity / mood), `CatalogIndexPublisher` (HTTP / noop) |
| Value Object | `ByteRange`, `PlaybackPosition`, `PublicationStatus`, `AccessPolicy`, `ContentHash` |
| Template Method | `LibraryEntryService` for watchlist & favorites |
| Facade | `AiServiceClient` hides the Python AI HTTP API |
| Optimistic locking | `WatchProgress` (`expectedVersion` protocol), catalog `opt_lock_version` |
| Event listener | `CatalogIndexBootstrapper` reacts to `ApplicationReadyEvent` and publishes the latest snapshot to the AI index |

## Configuration

`application.yml` defines `dippie.*` properties bound to `@ConfigurationProperties` records in each feature
(`JwtProperties`, `MediaProperties`, `AiProperties`, ...). Profiles: `demo` (default, H2 file DB, seeds demo accounts),
`mysql` (validate schema), `test` (H2 in-memory, used by tests).
