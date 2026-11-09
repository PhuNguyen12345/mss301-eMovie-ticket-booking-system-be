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
docker compose -p emovie-local -f compose.yml -f compose.local.yml \
  -f /tmp/emovie-fe-live-jars.yml up -d --no-build
```

The live FE preview is at `http://127.0.0.1:5174`; port 5173 is the visual demo.
Local admin/customer credentials are in the runtime directory's private
`.local-credentials.json`. This machine-specific snapshot is not part of the
checkout. The temporary override mounts executable JARs from the FE worktree;
see [MEMORY.md](MEMORY.md) for rebuild instructions, source revisions, local fixes and verified
integration limits.

For production, configure the hosting reverse proxy for `/api` and serve
`index.html` for FE routes, including `/payment/result`. For a separate API origin, keep it on the same site (same scheme and registrable
domain), set `VITE_API_BASE_URL` and allow the exact FE origin in backend CORS.
Browser refresh cookies use `SameSite=Strict`; unrelated FE/API sites are unsupported.
Keep `SESSION_COOKIE_SECURE=true` on HTTPS; use `false` only for local HTTP. `VITE_*` values are
public browser configuration, not secrets.

An explicit visual preview is available with:

```sh
npm run dev:demo -- --port 5173
```

Demo mode supplies sample movies and editable showtime schedules. It never creates
customer bookings, invents seat inventory, simulates payment success or accesses
private API data. Real booking uses API mode and the deployed cinema service.

## Screens and connected endpoints

| Screen                    | Route                             | Backend calls (under `/api`)                                                                                                 |
| ------------------------- | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Movie catalogue           | `/`                               | `GET /movies` with search, status and pagination                                                                             |
| Movie detail and sessions | `/movies/:movieId`                | `GET /movies/{id}`, `GET /showtimes`                                                                                         |
| Seat selection            | `/showtimes/:showtimeId/seats`    | `GET /showtimes/{id}`, `GET /bookings/showtimes/{id}/seats`, `POST /bookings`                                                |
| My bookings               | `/bookings`                       | `GET /bookings/me`                                                                                                           |
| Booking and checkout      | `/bookings/:bookingId`            | `GET /bookings/{id}`, `POST /bookings/{id}/cancel`, `POST /bookings/{id}/payments`, `POST /payments/bookings/{id}/sync`      |
| Payment return            | `/payment/result`                 | Retrieve booking and synchronize payment with backend                                                                        |
| Account                   | `/account`                        | `GET /users/me`, `POST /users/me/password/otp`, `POST /users/me/password/confirm`                                            |
| Admin schedules           | `/admin/showtimes`                | List/create/update/cancel showtimes, read/replace seat prices                                                                |
| Admin movies              | `/admin/movies`                   | `GET/POST /movies`, `PUT /movies/{id}` including publication status                                                          |
| Movie assets              | `/admin/movies/:movieId`          | `GET/PUT /movies/{id}/genres`, `GET/PUT /movies/{id}/credits`, `PUT /movies/{id}/poster`, `PUT /movies/{id}/poster/from-url` |
| Genres and people         | `/admin/genres`, `/admin/persons` | Paginated/searchable `GET`, `POST`, `PUT` and `DELETE` for `/genres` and `/persons`                                          |
| Admin refunds             | `/admin/refunds`                  | `GET /payments/refunds`, `POST /payments/refunds/{id}/complete`                                                              |
| Registration / reset      | `/register`, `/forgot-password`   | Register, resend OTP, verify registration, request password reset and confirm reset                                          |
| Cinema administration     | `/admin/cinema`                   | Cinema/screen/seat-type list/create/update; seat creation; cinema/screen/seat status changes                                 |
| Admin booking search      | `/admin/orders`                   | `GET /bookings` with code, status, user, showtime, date range and pagination                                                 |
| Combo administration      | `/admin/promotions`               | `GET /combos/all`, `POST /combos`, `PUT /combos/{id}`, activate/deactivate                                                   |
| Booking combos            | `/bookings/:bookingId`            | `GET /combos`, `PUT /bookings/{id}/combos`; totals come from the updated booking                                             |

Both customer and admin accounts use `POST /auth/login`. The admin layout checks
ADMIN role; the gateway remains the authorization authority. The branch includes
backend master through `b1bb100`, including auth routes, cinema catalogue, combo
APIs, admin booking search and ticket snapshots. It also fixes the three OTP email
senders so Spring discovers them as delivery ports. Deploy that fix with the
registration/reset screens; public routes alone are insufficient to deliver OTPs.

Showtime forms select a cinema and its rooms; changing cinema clears the room.
Customer/admin movie catalogues support server-side genre filtering. Genre options
follow every catalogue page; customer filters are stored in the URL and preserved
across search, status tabs and pagination. Showtime lists also accept a location
search; applying it resets the page and previous cinema/screen selection.
Admins can confirm a quick movie-status change through `PATCH /movies/{id}/status`.
Only status is sent, and failed writes retain the selection for an explicit retry.
Changing a movie's publication status does not cancel showtimes or refund bookings.
Seat prices use live seat-type names. Customers can filter schedules by cinema;
booking availability and holds come from booking-service. The cinema admin screen
manages physical facilities and seats, independently of sold/held seat inventory.
Cinema/screen status changes do not automatically cancel or refund existing orders.

Voucher, reporting, payment history and check-in screens use the merged backend APIs.
Profile editing remains a placeholder because no update endpoint is available.
Movie editors include genres, actor/director credits and poster uploads/import.
Reference pickers search and paginate on the server while preserving selected
records across pages. Failed saves retain edits; navigation asks before discarding
drafts and background updates cannot silently overwrite them. Poster uploads use
multipart JPEG/PNG files up to 5 MiB; external storage needs Cloudinary credentials.

## State and payment behavior

- Feature hooks own queries/mutations; reusable UI components handle fields,
  feedback, dialogs, pagination, access gating and placeholders.
- Zod validates API envelopes and response payloads. Query keys include filters
  and user identity for private data. Reads support cancellation and bounded
  retries; mutations are never retried automatically.
- Access tokens live only in memory. The browser logs in and refreshes through
  `POST /auth/login?transport=cookie` and `POST /auth/refresh?transport=cookie`.
  Refresh tokens stay in an HttpOnly cookie scoped to `/api/auth`; they are never
  returned to browser JavaScript or saved in localStorage/sessionStorage.
- Reload and payment returns restore the session before private screens load.
  Rotation is serialized within a tab and across tabs using Web Locks where
  available. Authentication changes notify other tabs through BroadcastChannel.
  Mutations wait for refresh and are never automatically replayed.
- Temporary restore failures show a retry; invalid/expired cookies require login.
  `POST /auth/logout` revokes the current browser refresh token and expires the
  cookie before clearing private data. A failed logout stays visible for retry.
  Logout does not revoke other devices or invalidate already issued access tokens;
  those expire normally. Existing JSON token login/refresh clients remain supported.
- Seat availability refreshes every 10 seconds. Holds and totals are authoritative
  backend responses. A 409 refreshes availability without replaying the hold.
- Pending booking detail refreshes every 5 seconds. The timer uses backend
  `expiredAt` in Vietnam time. Local timeout disables payment; the displayed
  booking state still comes from the server.
- Booking details and history prefer the server's stored ticket snapshot and
  seat names; older bookings fall back to catalogue reads. Admins viewing another
  customer's booking can read it without owner-only checkout/cancel controls.
- Combo selection is saved explicitly before payment. The backend determines
  price and total, and blocks changes while a payment link is pending. Failed
  writes retain the draft and are not replayed automatically.
- PayOS checkout requires an HTTPS PayOS URL. Configure the backend return and
  cancel URLs to this FE's `/payment/result`. `orderCode` identifies the booking
  using the backend's `bookingId * 100 + attempt` contract; query parameters never
  confirm payment. Returning users restore their session, then FE calls backend sync once
  and displays its result. Missing return identifiers lead to booking history.
- Refund completion records a manual transfer after explicit confirmation. It
  does not transfer money. QR images are fetched with authorization from the backend; the browser does not generate ticket codes.

## Structure

```text
src/
  app/                 Service composition, session lifecycle and lazy routes
  components/layout/   Customer shell, admin layout, access gates
  components/ui/       Shared fields, dialogs, buttons, feedback, placeholders
  features/auth/       Cookie restoration, access-token rotation and login
  features/movies/     Catalogue/detail contracts, hooks and screens
  features/movie-management/ Genre/person CRUD, association editors and poster uploads
  features/booking/    Seats, holds, history, checkout and payment return
  features/account/    Profile and password change
  features/admin/      Movie management and refund workflows
  features/cinema/     Catalogue contracts/hooks, venue selectors and facility admin
  features/combos/     Combo catalogue/admin and booking selection
  features/vouchers/   Wallet, claim, admin management and booking discounts
  features/operations/ Check-in, statistics, payment history and cancellation recovery
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

Current checks: 37 unit tests and 56 browser contract tests for cookie restoration,
reload/payment returns, concurrent tabs, logout failure, session
rotation races, multipart handling and movie-management drafts. Separately, live
Chromium verified registration/reset with MailHog OTPs, cinema/room/seat creation,
showtime pricing, a real seat hold, combo totals, cancellation and admin booking
search against local MySQL. Live checks also cover genre/person administration,
movie genre/credit assignment, customer detail and real refresh-token rotation.
Live checks also verify customer/admin genre filters, status-only updates with the
sample status restored afterwards, and location filtering against existing shows.
PayOS payment and Cloudinary upload remain unverified without credentials.

Live checks on 2026-11-09 additionally verified voucher creation/claim/application,
zero-total confirmation, authenticated ticket QR, check-in/replay rejection,
cancelled-showtime recovery, wallet and server reports/payment-history reads.
These bookings used 100% ticket discounts and did not create PayOS transactions.
The report and desktop/mobile screenshots are in runtime `live-results/operations-*`.

Live session checks against MySQL also verify reload, concurrent tabs, revocation
of old cookies, payment-return restoration and synchronized logout. The report is
in the local runtime's `live-results/session-report.json`.

## Newly connected backend APIs

| Screen                      | Route                  | Backend calls (under `/api`)                                                                   |
| --------------------------- | ---------------------- | ---------------------------------------------------------------------------------------------- |
| Voucher wallet              | `/vouchers`            | `GET /vouchers/me`, `POST /vouchers/claim`                                                     |
| Voucher management          | `/admin/vouchers`      | `GET/POST /vouchers`, `PUT /vouchers/{id}`, `POST /vouchers/{id}/{activate,deactivate,assign}` |
| Booking discounts           | `/bookings/:bookingId` | `POST /bookings/{id}/vouchers`, `DELETE /bookings/{id}/vouchers/{voucherId}`                   |
| Ticket QR                   | `/bookings/:bookingId` | Authenticated PNG `GET /bookings/{id}/qr`                                                      |
| Door check-in               | `/admin/check-in`      | `POST /bookings/check-in` after quantity confirmation                                          |
| Revenue reports             | `/admin/reports`       | `GET /bookings/statistics?from=...&to=...&groupBy=...`                                         |
| Payment history             | `/admin/payments`      | `GET /payments` with status, booking, order-code, date and page filters                        |
| Cancelled-showtime recovery | `/admin/cancellations` | `POST /bookings/showtimes/{id}/cancellation` after confirmation                                |

A voucher-covered booking is confirmed through the payment API. `NOT_REQUIRED`
means no PayOS checkout is needed; only the refreshed booking confirms the ticket.
The wallet and booking caches refresh after confirmation. The server computes all
reductions and totals. Failed mutations preserve input and are never replayed.

Check-in accepts manual entry or a hardware scanner typing into the code field;
it does not request camera access. Staff explicitly confirm the number entering,
and only a successful server response indicates admission. Cancelled-showtime
recovery displays failed-booking counts rather than claiming the entire batch succeeded.
Statistics use showtime dates; payment history uses transaction creation dates.
