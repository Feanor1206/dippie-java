# Dippie Java backend architecture and engineering rules

Built with Spring Boot 3.5, Java 21, JPA/Hibernate, stateless JWT via Spring Security, H2 for demo and tests, and MySQL 8 for production.

This module is the standalone Java backend for Dippie. It runs both the core business API and the runtime AI assistant in one JVM process: CineBot SSE (`/api/chat`), lexical catalog search and discovery (`/v1/search`, `/v1/discover`), mood and affinity recommendations (`/api/recommend`), and `/api/assistant/status`. It also serves the built React frontend from `frontend/`. The `ai/` directory remains the offline ML pipeline.

## Hard rules (every contributor, no exceptions)

1. Java code carries short Vietnamese `//` comments written for teammates (what a class is for, why a non-obvious choice was made). Names, small methods, value objects and enums still carry the intent; comments never restate the code. Frontend code has no comments.
2. The HTTP contract in `CONTRACT.md` is frozen. JSON field names, envelope vs raw bodies, status codes, and Vietnamese messages must match the Python version in meaning because the frontend depends on them.
3. Strict MVC layering (see below). Business rules live in `model` objects and `service` classes, never in controllers. Controllers only map HTTP requests to service calls and return `dto` records; they never touch a repository.
4. No Lombok. Use Java `record` types for DTOs. Entities use explicit constructors and intention-revealing methods (like `progress.advanceTo(...)` or `asset.isAccessibleBy(viewer)`), with no public setters for invariants.
5. Constructor injection only, with `final` fields and no field `@Autowired`.
6. Throw errors as `com.dippie.exception.*Exception` and let `GlobalExceptionHandler` render them into the standard envelope. Do not build error `ResponseEntity` objects by hand in controllers unless the contract requires a raw error body (catalog validate and media 416).
7. Database portability: code must run on both H2 (`MODE=MySQL`) and MySQL 8 against `database/schema/01_init.sql`. Avoid MySQL-only functions (`JSON_EXTRACT`, `HEX`) in queries. JSON columns are `LONGTEXT` mapped to `String`, parsed with Jackson in Java.
8. Time is UTC `LocalDateTime` from the injected `java.time.Clock`. Do not call `LocalDateTime.now()` directly in domain or service code.
9. Every feature package ships JUnit 5 tests. Domain rules get plain unit tests; HTTP behaviour gets `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` tests checking status codes and JSON.
10. The build must pass with `./mvnw -o -B test` from `backend/springboot`.

## Package layout (MVC, layer first)

```
com.dippie
├── DippieApplication
├── controller   C  REST controllers per feature (account, admin, assistant, catalog, library, media, movie,
│                   progress, recommendation, report, user, web). Map HTTP to service calls, return dto records.
│                   web also holds SpaController, HealthController and GlobalExceptionHandler.
├── service         Business logic per feature: AuthService, WatchProgressService, CatalogImportService,
│                   CatalogQueryService, RecommendationService + strategies, LocalCatalogSearch, LexicalIndex,
│                   ChatService, DiscoverService, MediaStreamService, MailDeliveryStrategy ...
├── repository  M  Spring Data JPA repositories and hand-written queries (AdminStatsQuery, ViewerSignalsRepository)
├── model       M  JPA entities (User, WatchProgress, Catalog*, MediaAsset ...), enums (Role, PublicationStatus),
│                   domain records and policies (CompletionPolicy, NextEpisodePolicy, EpisodeRuntime, MovieFilter)
├── dto         V  Request/response records exchanged with the React frontend (the View) as JSON
├── exception      DomainException hierarchy and feature exceptions, rendered by GlobalExceptionHandler
├── config         @Configuration and @ConfigurationProperties per feature
├── security       SecurityConfig, JwtService, JwtAuthenticationFilter, Viewer, CurrentViewer, rate limiters
└── common         collection, json, rating, text, web (ApiResponse, ClockConfig)
```

Layer rules (checked during review):

| Layer | May depend on |
| --- | --- |
| controller | service, dto, model, exception, security, config, common |
| service | repository, model, dto, exception, security, config, common, other services |
| repository | model, dto (JPQL constructor projections), common |
| model | model, exception, common |
| dto | dto, model, exception, common |

The View is the React 19 frontend in `frontend/`; `dto` records are the JSON it receives and sends.

## Design patterns used

| Pattern | Where |
| --- | --- |
| Strategy | `MediaLocationStrategy` (`DriveCacheLocation`, `DriveMountLocation`, `LocalMediaLocation`) via `MediaFileResolver`; `RecommendationStrategy` (`AffinityRecommendationStrategy`, `MoodRecommendationStrategy`); `CatalogIndexPublisher` (`LocalIndexPublisher`); `CatalogSearch` (`LocalCatalogSearch`); `AnswerGenerator` (`TemplateAnswerGenerator` / `DeepSeekAnswerGenerator`) |
| Value Object | `ByteRange`, `WatchDuration`, `WatchProgressCommand`, `PublicationStatus`, `AccessPolicy`, `ContentHash`, `ChatIntent`, `SearchConstraints` |
| Template Method | `AbstractLibraryListService` (`WatchlistService`, `FavoriteService`) |
| Optimistic locking | `WatchProgress` (`expectedVersion` protocol), catalog `opt_lock_version` |
| Event listener | `CatalogImportedEvent` (`CatalogReadModelCache`, `CatalogImportAuditListener`, `LocalCatalogSearch`) |

## Configuration

`application.yml` defines `dippie.*` properties bound to `@ConfigurationProperties` records under `com.dippie.config.<feature>` and `com.dippie.security` (`JwtProperties`, `MediaProperties`, `AssistantProperties`, and so on). Profiles: `demo` (default, file-backed H2 database, seeds demo accounts), `mysql` (validates schema), and `test` (in-memory H2 for tests).
