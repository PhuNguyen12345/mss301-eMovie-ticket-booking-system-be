# Cinema API

Swagger UI khi chạy cinema-service trực tiếp: `http://localhost:8083/swagger-ui/index.html`. OpenAPI JSON: `http://localhost:8083/v3/api-docs`.

Cinema service owns cinemas, screens, seat types, and physical seats. All responses use `ApiResponse` (`status`, `message`, `data`, `timestamp`). IDs are positive integers. Public GET requests and admin write requests are routed through the API gateway.

| Method | Path | Purpose |
| --- | --- | --- |
| GET / POST | `/api/cinemas` | List / create cinemas |
| GET / PUT | `/api/cinemas/{id}` | Read / update cinema metadata |
| PATCH | `/api/cinemas/{id}/status` | Set `ACTIVE` or `INACTIVE` |
| GET / POST | `/api/cinemas/{cinemaId}/screens` | List / create screens |
| GET / PUT | `/api/screens/{id}` | Read / update screen metadata |
| PATCH | `/api/screens/{id}/status` | Set `ACTIVE`, `INACTIVE`, or `MAINTENANCE` |
| GET / POST | `/api/seat-types` | List / create seat types |
| GET / PUT | `/api/seat-types/{id}` | Read / update a seat type |
| GET / POST | `/api/screens/{screenId}/seats` | List / create physical seats |
| GET | `/api/seats/{id}` | Read a physical seat |
| PATCH | `/api/seats/{id}/status` | Set `ACTIVE`, `INACTIVE`, or `MAINTENANCE` |

Create bodies:

```json
{"code":"C1","name":"Central Cinema","location":"Ha Noi"}
{"code":"S1","name":"Screen 1"}
{"code":"VIP","name":"VIP"}
{"rowCode":"A","seatNumber":1,"seatTypeId":1}
```

The first body belongs to cinemas, the second to screens, the third to seat types, and the fourth to seats. Status updates use `{"status":"INACTIVE"}`. Codes and row codes must be uppercase. Cinema `location` is required because showtime-service stores it with the showtime.

`GET /api/screens/{id}` returns `id`, `cinemaId`, `code`, `name`, and `status`. `GET /api/cinemas/{id}` returns `id`, `code`, `name`, `location`, and `status`. `GET /api/screens/{screenId}/seats` returns each seat's `id`, `screenId`, `rowCode`, `seatNumber`, `seatTypeId`, and `status`. These fields match the existing showtime and booking Feign clients.

The `(cinema_id, code)` screen key and `(screen_id, row_code, seat_number)` seat position are unique. A seat's position, screen, and type cannot be changed after creation, so bookings can retain its ID. A closed cinema or screen makes all of its seats appear `INACTIVE` in the screen seat list; physical seat status remains stored independently. Only booking-service owns seat holds and sold seat state.

Responses use HTTP 201 for creation, 400 for invalid input, 404 for missing resources, and 409 for duplicate codes or positions. Cinema service uses a dedicated MySQL database (`cinema_db`) and Flyway migrations.

On startup, `infrastructure/config/DataConfig.java` adds two sample cinemas (`DEMO_HN`, `DEMO_HCM`), two screens per cinema (`S1`, `S2`), `STANDARD` and `VIP` seat types, and 18 seats per screen. Rows `A` and `B` are standard; row `C` is VIP. Existing rows with the same keys are preserved, so restarting the service does not duplicate or overwrite them. Set `CINEMA_SEED_ENABLED=false` to disable sample data.
