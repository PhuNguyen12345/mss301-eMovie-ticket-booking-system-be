# eMovie web

Vietnamese customer booking and admin application built with React, TypeScript,
React Router, TanStack Query and Zod. The responsive dark UI uses locally bundled
Noto Sans Variable with Vietnamese glyphs.

Read [MEMORY.md](MEMORY.md) for the agreed scope and the checklist of backend
capabilities to connect later. Missing APIs use explicit placeholders; failed
API calls never silently substitute sample data or report a successful payment.

## Run against the API

```sh
npm ci
npm run dev -- --port 5173
```

API mode is the default. Copy `.env.example` to `.env.local` to override:

```dotenv
VITE_DATA_MODE=api
VITE_API_BASE_URL=/api
API_PROXY_TARGET=http://127.0.0.1:8080
```

Vite proxies `/api` to the gateway. Restart after changing environment variables.
On the current development machine, the live backend snapshot and database volumes
are managed from `.worktrees/local-runtime` in the main repository:

```sh
docker compose -p emovie-local -f compose.yml -f compose.local.yml up -d
```

The live FE preview is at `http://127.0.0.1:5174`; port 5173 is the visual demo.
Local admin/customer credentials are in the runtime directory's private
`.local-credentials.json`. This machine-specific snapshot is not part of the
checkout; see [MEMORY.md](MEMORY.md) for source revisions, local fixes and verified
integration limits.

For production, configure the hosting reverse proxy for `/api` and serve
`index.html` for FE routes, including `/payment/result`. Alternatively set
`VITE_API_BASE_URL` at build time and configure backend CORS. `VITE_*` values are
public browser configuration, not secrets.

An explicit visual preview is available with:

```sh
npm run dev:demo -- --port 5173
```

Demo mode supplies sample movies and editable showtime schedules. It never creates
customer bookings, invents seat inventory, simulates payment success or accesses
private API data. Real booking requires API mode and the cinema dependency.

## Screens and connected endpoints

| Screen                    | Route                          | Backend calls (under `/api`)                                                                                            |
| ------------------------- | ------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Movie catalogue           | `/`                            | `GET /movies` with search, status and pagination                                                                        |
| Movie detail and sessions | `/movies/:movieId`             | `GET /movies/{id}`, `GET /showtimes`                                                                                    |
| Seat selection            | `/showtimes/:showtimeId/seats` | `GET /showtimes/{id}`, `GET /bookings/showtimes/{id}/seats`, `POST /bookings`                                           |
| My bookings               | `/bookings`                    | `GET /bookings/me`                                                                                                      |
| Booking and checkout      | `/bookings/:bookingId`         | `GET /bookings/{id}`, `POST /bookings/{id}/cancel`, `POST /bookings/{id}/payments`, `POST /payments/bookings/{id}/sync` |
| Payment return            | `/payment/result`              | Retrieve booking and synchronize payment with backend                                                                   |
| Account                   | `/account`                     | `GET /users/me`, `POST /users/me/password/otp`, `POST /users/me/password/confirm`                                       |
| Admin schedules           | `/admin/showtimes`             | List/create/update/cancel showtimes, read/replace seat prices                                                           |
| Admin movies              | `/admin/movies`                | `GET/POST /movies`, `PUT /movies/{id}` including publication status                                                     |
| Admin refunds             | `/admin/refunds`               | `GET /payments/refunds`, `POST /payments/refunds/{id}/complete`                                                         |

Both customer and admin accounts use `POST /auth/login`. The admin layout checks
ADMIN role; the gateway remains the authorization authority. User-service PR #13
must be deployed for compatible `/api/auth` paths and HS256 tokens. Registration
and password-reset controllers exist, but their OTP/public routes are still
blocked by the gateway, so these entry points remain placeholders.

The cinema service is not implemented yet. Its missing physical-seat endpoint
blocks live seat maps/holds, and its cinema/screen lookups block schedule writes.
The FE consumes existing booking endpoints and shows a recoverable error when
those dependencies are unavailable. Admin cinema/screen/seat-type references stay
numeric until catalogue endpoints exist. Seat-price APIs require PR #14.

Placeholder screens also cover cinema administration, admin order search,
combos/vouchers, reports, profile updates and ticket check-in. Movie genre/person/
credit editing and file-based poster upload are not included in this iteration;
their backend APIs already exist. Poster/trailer URL fields are editable.

## State and payment behavior

- Feature hooks own queries/mutations; reusable UI components handle fields,
  feedback, dialogs, pagination, access gating and placeholders.
- Zod validates API envelopes and response payloads. Query keys include filters
  and user identity for private data. Reads support cancellation and bounded
  retries; mutations are never retried automatically.
- Tokens live only in memory. Reload, expiry and an authenticated 401 require
  login again. Logout clears the current session and private caches; server token
  revocation is pending a backend API.
- Seat availability refreshes every 10 seconds. Holds and totals are authoritative
  backend responses. A 409 refreshes availability without replaying the hold.
- Pending booking detail refreshes every 5 seconds. The timer uses backend
  `expiredAt` in Vietnam time. Local timeout disables payment; the displayed
  booking state still comes from the server.
- PayOS checkout requires an HTTPS PayOS URL. Configure the backend return and
  cancel URLs to this FE's `/payment/result`. `orderCode` identifies the booking
  using the backend's `bookingId * 100 + attempt` contract; query parameters never
  confirm payment. Returning users log in again, then FE calls backend sync once
  and displays its result. Missing return identifiers lead to booking history.
- Refund completion records a manual transfer after explicit confirmation. It
  does not transfer money. No secure ticket QR is fabricated from bookingCode.

## Structure

```text
src/
  app/                 Service composition, session lifecycle and lazy routes
  components/layout/   Customer shell, admin layout, access gates
  components/ui/       Shared fields, dialogs, buttons, feedback, placeholders
  features/auth/       In-memory session and customer/admin login
  features/movies/     Catalogue/detail contracts, hooks and screens
  features/booking/    Seats, holds, history, checkout and payment return
  features/account/    Profile and password change
  features/admin/      Movie management and refund workflows
  features/showtimes/  Schedule and price editing, HTTP/demo adapters
  lib/                 HTTP boundary, time utilities and navigation guards
```

## Validation

```sh
npm run format:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

An installed Chromium can be selected with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/absolute/path/to/chrome`. Browser tests start
fresh demo/API servers on **5183/5184**; keep these ports free.

Tests cover the HTTP/payment boundaries, price validation, admin scheduling,
customer booking/payment flow, seat conflicts, missing dependencies, expired
holds, role access, private-cache isolation, refund confirmation and mobile
layout. Browser API tests use mocked HTTP contracts and a mocked PayOS checkout;
they do not perform real transactions or replace live backend integration testing.
