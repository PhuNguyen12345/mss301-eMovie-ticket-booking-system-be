# Frontend project memory

Updated: 2026-11-07. Read this file before continuing FE work.

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

| Dependency | Current state | Revisit when available |
| --- | --- | --- |
| Cinema / screen / seat types | cinema-service is a skeleton; issue #10 assigned to docong2708 | Replace numeric admin references with real selectors, show venue and seat-type names; wire cinema admin CRUD |
| GET /api/screens/{id}/seats | Missing dependency of booking seat map / hold endpoints | Verify live seat selection, holds and availability conflicts; never fabricate seats on HTTP failure |
| GET /api/screens/{id}, GET /api/cinemas/{id} | Missing dependencies of showtime creation/update | Verify real schedule creation/update after cinema deployment |
| Auth compatibility | PR #13 pending: HS256 and /api/auth + /api/users aliases | Verify gateway login, customer/admin roles and profile with shared JWT settings |
| Registration / forgotten password | Controllers exist, but gateway blocks register/verify, register/resend-otp, password/forgot, password/reset | Replace auth placeholders after all four public POST routes are enabled; implement full OTP flow, not registration alone |
| Per-seat-type pricing | GET/PUT /api/showtimes/{id}/seat-prices in PR #14 | Merge/deploy and verify booking uses overrides, not only basePrice |
| Combos / vouchers | Database schema only, no API | Add catalogue, booking modifications and server-authoritative totals; do not calculate discounts locally |
| Profile updates | GET /api/users/me exists; update API missing | Replace read-only profile placeholder with an edit form |
| Server logout | Refresh-token revocation API missing | Replace local session clear with server revocation; do not claim all sessions revoked |
| Admin booking search / reporting | No endpoints | Wire order search and real revenue statistics; do not derive totals from one page |
| Ticket check-in | No validation/check-in endpoint | Add check-in only with server-issued validation; bookingCode is a reference, not a fabricated secure ticket |
| Cancel paid showtime | No cross-service booking/refund workflow | Coordinate cancellation policy with booking before offering automatic refunds |

Existing booking endpoints: GET /bookings/showtimes/{id}/seats, POST /bookings,
GET /bookings/me, GET /bookings/{id}, POST /bookings/{id}/cancel,
POST /bookings/{id}/payments, POST /payments/bookings/{id}/sync.
Existing refund endpoints: GET /payments/refunds?status=..., POST
/payments/refunds/{id}/complete. Refund completion records a manual transfer;
it does not transfer money.

## Integration rules

- API mode is the default; explicit demo mode is for visual review only.
- Tokens remain in memory. Reload / PayOS return may require login again.
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
- Registration, forgot-password, cinema administration, order search, promotions,
  reports, profile editing and check-in are explicit placeholders.
- Movie genres/persons/credits editing and multipart poster upload have backend
  APIs but are not yet exposed as dedicated FE admin screens. Movie poster/trailer
  URL fields are editable. Keep these distinct from missing-backend placeholders.

## Local verification

`npm test`, `npm run build`, `npm run format:check`, `npm run test:e2e`.
Playwright uses fresh demo/API servers on 5183/5184 to avoid the review preview.
Use `npm run dev:demo -- --port 5173` for sample catalogue/showtime visual review;
use normal `npm run dev` for real API mode. The demo cannot book or pay.
