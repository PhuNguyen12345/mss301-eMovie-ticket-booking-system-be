# Showtime seat prices

## Read prices

`GET /api/showtimes/{id}/seat-prices` is public through the API gateway.

```json
{
  "status": 200,
  "message": "Seat prices retrieved successfully",
  "data": [
    {"id": 77, "seatTypeId": 1, "price": 90000},
    {"id": 78, "seatTypeId": 2, "price": 150000}
  ]
}
```

Prices are ordered by `seatTypeId`. `id` is the price row's primary key,
which booking stores in `booking_seats.showtime_seat_id`.
An existing showtime without configured prices returns `200` with `data: []`.
Booking uses `basePrice` when a seat type has no configured price.
A missing showtime returns `404` in the shared API response envelope.
The fields in `GET /api/showtimes/{id}` are unchanged.

## Replace prices

`PUT /api/showtimes/{id}/seat-prices` requires an admin access token through
the API gateway, like the other showtime write endpoints. Services must remain
on the internal network so callers cannot bypass gateway authorization.

```json
[
  {"seatTypeId": 1, "price": 90000},
  {"seatTypeId": 2, "price": 150000}
]
```

The response is `200` with message `Seat prices updated successfully` and
the complete price list in the same format as GET.

- The array replaces all configured prices for this showtime. Omitted types
  are removed; `[]` clears prices so booking falls back to `basePrice`.
- Existing types retain their row IDs and creation timestamps. A type removed
  and later re-added receives a new ID. Existing booking price snapshots are unchanged.
- Each `seatTypeId` must be a positive integer and appear only once.
- Prices must be between `0` and `9999999999.99`, with at most two decimal places.
- Invalid input returns `400`; a missing showtime returns `404`.
- Updates are transactional and serialized per showtime; the last successful
  replacement determines the complete price list.

Flyway migration `V2__showtime_seat_prices.sql` creates the table, unique key,
showtime foreign key and non-negative price check. Seat types belong to the
cinema service, so there is no cross-database foreign key for `seatTypeId`.

## Verification

```sh
mvn -pl services/showtime-service,api-gateway -am test
```

The MySQL integration suite also verifies migration constraints, ID retention,
replacement, clearing, isolation between showtimes, and concurrent writes.
Enable it with `SHOWTIME_TEST_DB_URL`, `SHOWTIME_TEST_DB_USERNAME`, and
`SHOWTIME_TEST_DB_PASSWORD`, pointing to a disposable MySQL database.
