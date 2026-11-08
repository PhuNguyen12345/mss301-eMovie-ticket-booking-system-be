# Showtime: Clean Architecture and rich domain

This refactor follows AgriContract's separation of pure domain models,
repository contracts, application use cases, and persistence adapters. In
particular, its product and user services keep JPA entities and mapping in
infrastructure. Showtime goes further by keeping Spring annotations out of
both domain and application; infrastructure configuration wires the use cases.

```text
fu.mss301.showtime
  domain/
    model/          Showtime aggregate, immutable SeatPrice children,
                    Schedule, Price, Movie and Venue value objects
    repository/     ShowtimeRepository (aggregate-only persistence contract)
    exception/      Business conflicts, without HTTP dependencies
  application/
    usecase/        Scheduling, rescheduling, cancellation, queries and pricing
    dto/            Framework-free commands, views, search and pagination
    port/           MovieCatalog, CinemaCatalog, ScreenSchedule,
                    ShowtimeQueries, BookingNotifier and Transactions
    exception/      Missing resources and unavailable upstream dependencies
  infrastructure/
    persistence/    JPA entities, Spring Data repositories, mapping and adapters
    client/         Feign clients, validated catalog adapters and the
                    booking notifier
    config/         Spring transaction adapter, Clock and use-case wiring
  presentation/
    controller/     HTTP binding and shared ApiResponse envelope
    exception/      Exception-to-HTTP mapping
```

Dependencies point inward: presentation calls application; application depends
on domain and ports; infrastructure implements those ports. Domain and
application import only Java and their permitted inner packages. Architecture
tests guard these rules, aggregate construction, and the absence of setters.

## Aggregate and rules

`Showtime` owns its schedule, lifecycle and seat prices. Its constructor is
private. New shows use `schedule`; existing shows change through `reschedule`,
`cancel` and `replaceSeatPrices`. Persistence uses `restore` to reconstruct
existing shows, including shows that have already started.

- `Price` enforces non-negative DECIMAL(12,2) values and normalizes scale.
- `Schedule` validates time intervals, future scheduling and overlap rejection.
- `Movie` and `Venue` own availability, release-date and screen/cinema rules.
- `Showtime` protects started/cancelled shows, cancellation idempotency and
  unique seat types. Failed price validation leaves the aggregate unchanged.
- Seat prices are immutable children. Collections are defensively copied;
  callers cannot mutate them or persist them independently through domain APIs.
- Retained seat types keep IDs and creation timestamps. Replacement removes
  omitted types; an empty list restores booking's base-price fallback.

The domain exposes one repository, for `Showtime`. The private implementation
uses two Spring Data repositories to map the existing tables; these are
technical persistence details and are never injected into application code.
No schema migration is needed for the refactor. No domain event is introduced
because no existing workflow requires a cross-aggregate reaction.

## Notifying booking-service of a cancellation

After the cancellation transaction commits, `ShowtimeServiceImpl` calls the
`BookingNotifier` port. `FeignBookingNotifier` sends
`POST /internal/showtimes/{id}/cancellation` to booking-service on a virtual
thread, so the admin's cancel request does not wait for every booking to be
handled (the Feign read timeout for booking-service is 60 seconds). A failure
is only logged as `WARN`: the showtime stays cancelled, booking-service checks
the showtime status itself and handles a repeated call without compensating
twice, and admins re-run it with
`POST /api/bookings/showtimes/{id}/cancellation`. Cancelling an already
cancelled show notifies again. Set `BOOKING_SERVICE_URL` to bypass Eureka, as
the smoke stack does with the contract stub.

## Transactions and concurrency

Movie and cinema lookups finish before opening a write transaction. The
transaction adapter uses READ_COMMITTED for writes. Inside the transaction:

1. Updates lock the showtime row; creations lock the target screen schedule.
2. Screen moves lock old/new screens in ascending ID order.
3. The aggregate rechecks time-sensitive rules after locks are acquired.
4. Overlap queries run while holding the screen lock.
5. The aggregate and its price children are saved in the same transaction.

Price replacements acquire the same showtime row lock as rescheduling and
cancellation, including when the price list is initially empty. Queries use a
separate read port so paginated listing does not load every show's prices.

## Compatibility and verification

HTTP routes, fields, statuses, envelopes, pagination, ordering, database
schema, and booking's price row IDs remain compatible with PR #14. Price
replacement retains its existing lifecycle policy: it can update prices on
started/cancelled shows and does not alter showtime metadata. Restricting that
behavior would be a separate business change.

Tests cover pure domain invariants without Spring, forbidden dependencies,
existing controller contracts, gateway roles, MySQL migration constraints,
price ID retention, aggregate persistence, and concurrent scheduling/pricing.
The MySQL suite also asserts remote lookups run outside a DB transaction.

```sh
SHOWTIME_TEST_DB_URL=jdbc:mysql://127.0.0.1:13316/showtime_test \
  mvn -pl services/showtime-service,api-gateway -am clean test --no-transfer-progress
```

Set `SHOWTIME_TEST_DB_USERNAME` / `SHOWTIME_TEST_DB_PASSWORD` if different from
the defaults. Use a disposable database; the integration suite clears test data.
