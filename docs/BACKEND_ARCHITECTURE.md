# Backend organization

eMovie follows AgriContract's separation of responsibilities, using eMovie's existing
dependencies and integrations. This does not require AgriContract libraries,
Keycloak, RabbitMQ, or a new event framework. Existing booking/user event flows stay
in place; this refactor introduces no messaging technology.

```text
domain/                       Business state, invariants, repository contracts
application/
  usecase/                    Orchestration through domain methods and ports
  port/in/                    Operations exposed to adapters, where useful
  port/out/                   Persistence/integration/transaction contracts
  dto/                        Commands and response data
infrastructure/
  web/                        Controllers, HTTP validation and exception mapping
  persistence/                JPA entities, repositories, mapping and adapters
  config/                     Spring wiring and transaction configuration
  storage/, client/, ...      Existing provider integrations
```

Booking groups use cases by `booking`, `payment`, and `combo` instead of putting
everything under one `usecase` package. Domain repository contracts may stay in
`domain/repository`; an application query port may return application DTOs. These
variations do not change the dependency direction.

Domain never imports application or infrastructure. Application never imports
infrastructure, Spring, JPA, or HTTP binding types. Infrastructure implements ports
and creates the use cases. Existing Lombok annotations and plain shared pagination
DTOs remain where already used; they are not custom AgriContract dependencies.

## Movie

`Movie` owns metadata, genre selection and credits. `MovieDetails`, `Genre`,
`Person`, and `Credit` validate business data. A rejected replacement leaves the
aggregate unchanged. Credit identity is retained for the same person and role;
duplicate selections and director character names are rejected by the domain.

`MovieUseCase`, `GenreUseCase`, `PersonUseCase`, and `MovieRelationsUseCase`
coordinate ports inside a transaction. JPA classes are separate persistence models.
The existing schema, locks, API paths, response DTOs and HTTP error mapping remain.
Reference locks are acquired in ID order when replacing movie relationships.

`MoviePosterService` is an application workflow using `ImageStorage` and
`Transactions`. Cloudinary upload runs outside a database transaction. The poster
pointer is replaced in a transaction; the previous asset is deleted after commit.
Definite failure cleans up the new asset. An uncertain commit retains both assets
for reconciliation, preserving the existing failure policy.

## User and Cinema

User models expose named operations for OTP attempts, expiry, resend, confirmation,
refresh-token consumption and password changes instead of public setters. Builders
remain for persistence rehydration and test fixtures. Use cases hash/compare secrets,
call stores, and translate domain outcomes into the existing application responses.

Cinema models validate facility data and derive seat visibility from cinema/screen
status. `CinemaUseCase` has no Spring annotations. Infrastructure applies read-only
transactions to queries and write transactions to `create*`, `update*`, and `set*`.
New command methods must be included in that transaction policy.

## Verification

Each business service has architecture tests guarding dependency direction. Domain
tests exercise business transitions without Spring; controller and use-case tests
preserve API and workflow behavior.

Run the tests without database-backed suites:

```sh
mvn -pl services/movie-service,services/user-service,services/cinema-service,services/showtime-service,services/booking-service -am test '-Dtest=*,!MovieServiceApplicationTests,!UserServiceApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```

For real MySQL tests, use disposable schemas. `MoviePersistenceTests` deletes its
fixture tables; existing booking/showtime tests also reset data. Set
`MOVIE_TEST_DB_URL`, `CINEMA_TEST_DB_URL`, `BOOKING_TEST_DB_URL`, and
`SHOWTIME_TEST_DB_URL`, plus the corresponding `_USERNAME` and `_PASSWORD` variables.
Movie/Cinema defaults are `root` / `architecture_test`; Booking/Showtime retain
their existing defaults. Movie integration tests replace the image-storage port
with a fake and verify persisted relations, credit IDs, rollback and poster cleanup.
Cinema integration tests verify persistence and derived seat availability.

The original Movie/User context tests use normal service environment variables
(`DB_*`, `USER_DB_*`, `CLOUDINARY_*`, `SMTP_FROM`). Disable Eureka for local test runs
with `-Deureka.client.enabled=false`. No real Cloudinary, SMTP, or payment credentials
are needed for the tests.
