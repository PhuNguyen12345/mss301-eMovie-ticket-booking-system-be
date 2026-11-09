# Frontend project memory

Updated: 2026-11-09. Read this file before continuing FE work.

## Agreed scope

- Build the customer movie / showtime / seat selection / booking / PayOS payment /
  booking-history flow and a separate admin area, not just a showtime demo.
- Use existing backend APIs. Missing capabilities must be explicit placeholders,
  with no fake successful writes, fabricated inventory or payment confirmation.
- Dark theme, Vietnamese-capable Noto Sans, restrained UI, reusable components,
  feature hooks, typed API boundary and TanStack Query server state.
- Keep FE changes on feat/showtime-admin-demo (name predates expanded scope).
  Use small commits and push. Show the UI before opening the FE PR.

## API dependencies to revisit

| Dependency                                   | Current state                                                                                              | Revisit when available                                                                                      |
| -------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Cinema / screen / seat types                 | Merged in master; connected to /admin/cinema, dependent showtime selectors and customer venue names/filter | No delete or seat-position/type update endpoints; do not invent those operations                            |
| GET /api/screens/{id}/seats                  | Available; real physical seat creation and booking hold verified                                           | Keep physical status separate from per-showtime availability                                                |
| GET /api/screens/{id}, GET /api/cinemas/{id} | Available; real showtime creation verified                                                                 | Keep screen selection scoped to its cinema                                                                  |
| Auth compatibility                           | HS256 and /api/auth + /api/users aliases merged; live login verified                                       | Access token in memory; refresh credential in HttpOnly cookie                                                                               |
| Registration / forgotten password            | Gateway routes enabled; full registration, resend, verify, forgot/reset connected                          | OTP sender wiring fix is included on this FE branch; carry it into deployment with these screens            |
| Per-seat-type pricing                        | Merged and connected to live seat-type catalogue                                                           | Live hold verified override price 125000 rather than base price 90000                                       |
| Combos                                       | Public/admin catalogue, create/update, activate/deactivate and booking replacement connected               | Backend controls totals and disallows changes while a payment link is pending                               |
| Vouchers                                     | Merged; management, assignment, claim, wallet and booking apply/remove connected                                                                               | Use backend totals; NOT_REQUIRED confirms zero-total bookings without PayOS                                                |
| Profile updates                              | GET /api/users/me exists; update API missing                                                               | Replace read-only profile placeholder with an edit form                                                     |
| Server logout                                | Cookie logout and current refresh-token revocation implemented                                                                       | Only the current browser session is revoked; issued access tokens expire normally                       |
| Admin booking search                         | GET /api/bookings merged and connected to /admin/orders with code/status/user/showtime/date filters        | Search and pagination run on the server; private cache includes user identity                               |
| Reporting                                    | Statistics and payment-history APIs merged and connected                                                                                               | Server totals grouped by showtime day/month/movie/cinema; payment filters and pagination are server-side                                             |
| Ticket details                               | Booking response now includes ticket, named seats and combos                                               | Render stored ticket snapshot first; older bookings fall back to catalogue lookup                           |
| Ticket check-in                              | Authenticated PNG QR and admin check-in APIs connected                                                                            | QR comes from the backend; explicit quantity/confirmation; failures never admit customers |
| Cancel paid showtime                         | Showtime cancellation notifies booking; admin recovery API connected                                                                   | Display SHOWTIME_CANCELLED and voucher compensation; recovery reports partial failures                               |

Core booking endpoints: GET /bookings/showtimes/{id}/seats, POST /bookings,
GET /bookings/me, GET /bookings/{id}, POST /bookings/{id}/cancel,
POST /bookings/{id}/payments, POST /payments/bookings/{id}/sync.
Existing refund endpoints: GET /payments/refunds?status=..., POST
/payments/refunds/{id}/complete. Refund completion records a manual transfer;
it does not transfer money. Voucher refunds display their method and voucher ID; they have no manual-completion action.

## Integration rules

- API mode is the default; explicit demo mode is for visual review only.
- Browser access tokens remain in memory. HttpOnly refresh cookies restore the
  session on reload / PayOS return. Secure cookies default on; local HTTP uses
  SESSION_COOKIE_SECURE=false. Deploy FE/API on the same site, preferably /api proxy.
- Access tokens refresh before expiry through POST /auth/refresh?transport=cookie, with one shared
  refresh request and no mutation replay. Logout/new login invalidates pending
  refresh results; routine rotation preserves private caches and booking ownership.
- Private query keys include user identity; clear private caches on logout or
  expiry. Never persist payment credentials or access tokens in browser storage.
- Never trust PayOS URL success/cancel parameters as payment status. Reconcile
  through the backend and display its booking status.
- Mutations must not auto-retry, including holds, payment initiation and sync.
- Display times in Asia/Ho_Chi_Minh, matching the backend LocalDateTime contract.
- Browser API tests use HTTP mocks; report this separately from live integration.

GitHub: https://github.com/PhuNguyen12345/mss301-eMovie-ticket-booking-system-be

## Implemented FE routes

- `/`: searchable movie catalogue; `/movies/:movieId`: detail and paged showtimes.
- `/showtimes/:showtimeId/seats`: live availability, selection and hold.
- `/bookings`, `/bookings/:bookingId`: personal history, detail, cancellation,
  payment initiation and synchronization; `/payment/result`: backend reconciliation.
- `/account`: profile read and current-password + email OTP password change.
- `/admin/showtimes`: schedule create/update/cancel and per-seat-type prices.
- `/admin/movies`: list/create/update movie metadata and publication status.
- `/admin/refunds`: pending/completed/rejected list and manual-completion confirmation.
- `/register`, `/forgot-password`: real email OTP registration/resend/verification
  and password reset. Success requires the final verification/reset API response.
- `/admin/cinema`: create/update cinemas, screens and seat types; create physical
  seats; change cinema/screen/seat status. A status change does not refund bookings.
- `/admin/orders`: admin booking search with server-side pagination and filters.
- `/admin/promotions`: combo create/update and activate/deactivate; customers can
  select combos in pending bookings before payment, using backend totals.
- `/vouchers`: personal wallet and claim codes, including value vouchers from compensation.
- `/admin/vouchers`: create/edit, activate/deactivate, assign to a customer, server pagination.
- `/admin/reports`: server statistics by showtime date and grouping; no totals derived from a page.
- `/admin/payments`: payment-history filters/pagination with manual/voucher refund details.
- `/admin/check-in`: explicit code/quantity confirmation; server-authoritative admission history.
- `/admin/cancellations`: explicit recovery for a cancelled showtime, with counts and partial failures.
- Booking detail applies/removes vouchers, confirms zero-total orders through the payment API,
  downloads authenticated QR images, and displays admitted counts / cancelled-showtime compensation.
- Profile editing remains a placeholder because the update API is still unavailable.
- `/admin/genres`, `/admin/persons`: searchable, paginated catalogue CRUD with
  delete confirmation and visible server conflicts.
- `/admin/movies/:movieId`: genre assignment, actor/director credits and poster
  upload/import from URL. Shared pickers retain selected records across pages;
  drafts survive failed saves and block navigation until confirmed. Background
  changes do not overwrite unsaved edits. Customer detail displays saved credits.
- Poster uploads accept JPEG/PNG up to 5 MiB, matching the backend. Cloudinary
  success still requires credentials; failed uploads never report success.
- Customer/admin movie catalogues filter by `genreId`. Shared genre options follow
  server pagination; customer URL filters survive search, status tabs, pagination
  and browser history. Changing a filter resets the page.
- Customer/admin showtimes filter by `location` through the backend. Applying a
  location clears the previous cinema/screen selection and resets pagination;
  newly created/edited showtimes clear the location filter so the result is visible.
- Admin movies offer a confirmed status-only PATCH with retained selection on
  failure. This changes publication status only, without cancelling/refunding
  existing showtimes/bookings.

## API audit (2026-11-08)

- Audited master at `9b58f53`; its new change only rejects malformed PayOS webhooks.
  This fix is now merged into the FE branch and included in the local runtime.
  Their backend baseline is `9b58f53`. The catalogue additions use existing APIs.
- `POST /movies/{id}/expire` is redundant with PATCH status EXPIRED and is not
  called separately. PayOS webhooks are called by PayOS, never by FE.
- Detail GETs for genres/persons/seat-types/seats remain available for future
  dedicated detail screens; current editors use the corresponding list records.
  These are not missing backend APIs. Historical audit: these voucher endpoints were not available then; they are connected in the 2026-11-09 update.

## Local verification

`npm test`, `npm run build`, `npm run format:check`, `npm run test:e2e`.
Playwright uses fresh demo/API servers on 5183/5184 to avoid the review preview.
Use `npm run dev:demo -- --port 5173` for sample catalogue/showtime visual review;
use normal `npm run dev` for real API mode. The demo cannot book or pay.

## Running local backend (2026-11-07)

- FE branch includes master through `9b58f53` (including booking search, combos,
  clean booking refactor and ticket snapshots). The default branch is `master`.
- Runtime snapshot: `.worktrees/local-runtime` under the main repository, updated
  to the same backend APIs. This directory is ignored and specific to this machine.
- Start from that directory with `docker compose -p emovie-local -f compose.yml
-f compose.local.yml up -d`. Gateway: `http://127.0.0.1:8080`; MailHog:
  `http://127.0.0.1:8026`; frontend using live APIs: `http://127.0.0.1:5174`.
  Start the FE with `VITE_DATA_MODE=api npm run dev -- --port 5174 --strictPort`.
  Port 5173 can be used for the separate explicit visual demo.
- Five MySQL databases run with persistent Docker volumes: user, movie, showtime,
  booking and cinema. Four sample movies were created through the movie API. Local admin and
  customer accounts were registered and OTP-verified using MailHog. Credentials
  are in runtime `.local-credentials.json` (private, ignored; never commit it).
- The three OTP email senders originally omitted `OtpDeliveryPort` and `eventType`,
  causing `No projection handler`. The fix and a concrete-adapter discovery test
  are now committed with this branch; runtime uses the same fix. Compose JWT
  interpolation is already corrected in master.
- Validation: 33 FE unit tests, 45 browser contract tests, production build and
  formatting pass; 9 focused Java OTP tests pass. Contract browser tests use mocks.
- Live Chromium separately verified real gateway/MySQL flows without mocks:
  registration + MailHog OTP verification, password reset + OTP, admin login,
  cinema/screen/seat-type/seat creation, showtime creation and price override,
  customer seat hold, ticket snapshot, combo creation/selection, booking cancel,
  admin search and mobile layout. Confirmed total: 125000 + 2 * 45000 = 215000.
- Live artifacts: runtime `live-results/report.json`, `cinema.png`, `booking.png`,
  `orders-mobile.png`. The live-test account is private `.integration-account.json`;
  do not commit credentials. The test booking was cancelled; its seat is released.
- Additional live Chromium checks passed for genre create/update/search/delete,
  person creation, movie creation and genre/actor/director assignments, customer
  detail/mobile layout, and real refresh-token rotation followed by a successful
  authenticated profile request. Artifacts: runtime `live-results/movie-report.json`,
  `movie-assets.png`, `movie-detail-mobile.png`. Local sample records remain in DB.
- Real PayOS checkout and Cloudinary uploads still need credentials and remain
  unverified. No payment was simulated or marked paid in the database.
- Live checks on 2026-11-08 verified customer/admin genre filters, a real
  status-only PATCH (sample movie restored afterwards), and location filtering
  returning an existing showtime. Browser contract tests additionally cover filter
  pagination/history, dependent venue resets and rejected status changes.
  Artifacts: runtime `live-results/catalogue-filter-report.json`,
  `catalogue-filter-mobile.png`, `location-filter-admin.png`.

## Browser session completion (2026-11-08)

- Cookie login/refresh returns only access tokens to JS; JSON token clients remain
  compatible. Logout revokes the current refresh token and removes its cookie.
- Restore gates private screens; temporary failures offer retry. Mutations wait
  for refresh but are never replayed. In-tab serialization plus Web Locks prevent
  parallel rotations; BroadcastChannel synchronizes login/logout across tabs.
- Unit regressions cover restore races, expired cookies, transient errors and
  logout during restore. Browser contracts cover F5, payment return, multiple
  tabs, cookie visibility, failed restore and failed logout.
- Completed verification: 33 FE unit tests, 45 mocked browser tests, production
  build and formatting pass. Gateway/user Java suite: 126 tests pass (the
  environment-dependent user context test is excluded from that Maven run).
- Updated user-service and gateway were separately started with the real MySQL
  stack. Live Chromium verified login, account reload, replay rejection for old
  cookies, simultaneous tab reload, external navigation to payment return, and
  logout revocation including the other tab. Report: runtime
  `live-results/session-report.json`; no JS errors.
- Authenticated movie-status PATCH returned 200 through the live gateway with
  the original status preserved. Issue #18 is still open; this successful probe
  does not establish its root cause or close the issue.
- PayOS checkout still requires credentials. The webhook-signature fix from
  master is integrated; no real payment or Cloudinary upload was performed.
- Post-merge PayOS adapter tests: 4/4 pass. Live gateway rejects a webhook
  with missing signature with HTTP 400; booking-service is healthy after rebuild.

## New API integration (2026-11-09)

- FE branch merged master through `b1bb100` (voucher, check-in, showtime cancellation,
  statistics, payment history and the now-merged BE architecture PR #44).
- The merge removed refresh-credential setters used by browser logout. Logout now
  calls the domain revoke method, preserving idempotency and allowing expired or
  inactive-account credentials to be revoked. Domain regression coverage is added.
- Post-merge gateway/user suite: 142 tests passed (excludes the environment-dependent
  `UserServiceApplicationTests`); all seven application JARs rebuilt cleanly.
- API clients validate new booking/payment states and fields. A NOT_REQUIRED response
  must have zero amount and null transaction/checkout fields. QR uses bearer-authenticated
  PNG fetches and revokes browser object URLs when released.
- Private queries include user identity. Booking confirmation invalidates booking and
  voucher caches so the wallet shows USED immediately. Writes have no automatic retries.
- Browser tests cover apply/remove, seat-specific vouchers, zero-total confirmation,
  failed QR/claim/application, voucher drafts and assignment, partial check-in and replay,
  server reports/payment pagination, cancellation partial failures and role restrictions.
- Final verification: 56 mocked browser tests, 37 unit tests, production build and
  formatting pass, including wallet refresh after zero-total confirmation.
- Live Chromium against gateway/MySQL verified voucher create/claim/apply,
  NOT_REQUIRED confirmation and real QR PNG, check-in and duplicate rejection,
  showtime cancellation/recovery, cancelled booking/wallet, statistics grouped by
  movie and payment-history reads. Repeated successfully after merging `b1bb100`,
  including cookie restoration, logout and rejection of the revoked cookie.
  No browser errors. Artifacts are runtime
  `live-results/operations-report.json`, `operations-ticket-mobile.png`,
  `operations-checkin.png` and `operations-reports.png`.
- These live bookings used 100% ticket discounts, without a PayOS transaction.
  Real PayOS checkout and Cloudinary uploads remain unverified; profile editing
  still has no backend endpoint.
- Current local backend uses executable JARs from the FE worktree mounted through
  `/tmp/emovie-fe-live-jars.yml`. From the runtime directory, restart with
  `docker compose -p emovie-local -f compose.yml -f compose.local.yml
  -f /tmp/emovie-fe-live-jars.yml up -d --no-build`. The override is machine-local;
  rebuild JARs with `mvn install -DskipTests` followed by `mvn -o -pl
  discovery-server,api-gateway,services/booking-service,services/cinema-service,services/movie-service,services/user-service,services/showtime-service
  clean package spring-boot:repackage -DskipTests` before recreating app containers.
