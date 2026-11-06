# eMovie showtime administration

A Vietnamese showtime and seat-pricing workspace built with React, TypeScript
and Vite. The responsive dark interface uses locally bundled Noto Sans Variable,
including Vietnamese glyphs.

## Run the demo

```sh
npm ci
npm run dev -- --port 5173
```

Open http://127.0.0.1:5173. Demo mode is the default. It supports movie, date,
cinema, screen and status filters, pagination, price editing, creation and
cancellation. Unsaved prices require confirmation before navigation. Sample
data persists in localStorage; demo mode never writes to the backend.

## Connect the API

Copy `.env.example` to `.env.local`, then set:

```dotenv
VITE_DATA_MODE=api
VITE_API_BASE_URL=/api
API_PROXY_TARGET=http://127.0.0.1:8080
```

Restart Vite after changing environment variables. The development proxy forwards
`/api` to the gateway. For production, configure the hosting reverse proxy or
set `VITE_API_BASE_URL` to a backend URL with appropriate CORS support at build
time. `VITE_*` values are public browser configuration; never put secrets there.

The HTTP adapter uses these existing contracts:

| Operation                  | Endpoint                                    |
| -------------------------- | ------------------------------------------- |
| Admin login                | `POST /api/auth/login`                      |
| Movie catalogue, all pages | `GET /api/movies`                           |
| Filtered showtimes         | `GET /api/showtimes`                        |
| Create showtime            | `POST /api/showtimes`                       |
| Cancel showtime            | `POST /api/showtimes/{id}/cancel`           |
| Read / replace seat prices | `GET / PUT /api/showtimes/{id}/seat-prices` |

Responses use `{ status, message, data }`; Zod validates envelopes and payloads.
Live mode reports backend failures and never substitutes demo fixtures.
The cinema service does not yet expose catalogue endpoints: cinema, screen and
seat-type references use numeric IDs. The demo supplies Standard/VIP/Couple labels.

Writes require an ADMIN login. Bearer tokens live only in memory; refreshing the
page, expiry or an authenticated 401 requires login again. Successful login
refreshes queries. The backend remains responsible for authorization and business
validation. There is no automatic refresh-token flow or replay of failed writes.

## Organization

```text
src/
  app/                         Service composition, session and query providers
  components/ui/               Shared buttons, fields, dialogs and feedback
  features/auth/               Session store and login dialog
  features/showtimes/
    api/                       Demo and HTTP repository adapters
    components/                Schedule, filters, price editor and dialogs
    hooks/                     Query/mutation hooks, filters and price drafts
    model/                     Contracts, schemas and repository interface
  lib/                         HTTP boundary, date formatting and toast hook
```

Components consume hooks rather than fetching directly. TanStack Query owns
server state with filter-aware keys, cancellation, bounded read retries and
explicit invalidation after writes. Mutations are not retried automatically.
Price saves cancel stale reads and update the cache from the server response.
Editable drafts stay separate from cached data so background reads do not
overwrite unsaved input. The HTTP client handles timeouts, authentication and
typed failures independently of presentation.

## Validation

```sh
npm run format:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

An installed Chromium can be selected using
`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/absolute/path/to/chrome`.
Playwright uses ports 5173 (demo) and 5174 (API). Keep these ports free for a
fresh run; locally, an existing server on either port must use the matching mode.

Unit tests cover the HTTP boundary and price validation. Demo browser tests cover
filters, persisted prices, overlap rejection, creation/cancellation, keyboard
dialogs, mobile layout, dark theme, font loading and unsaved drafts. API browser
tests mock HTTP contracts to verify pagination, filters, Bearer authentication,
write failures, cache updates, expired sessions and malformed responses. These
checks do not constitute an end-to-end run against deployed backend services.

The FE branch starts from `feat/issue-11-showtime-seat-prices`; it remains a demo
for review independently of the backend Clean Architecture / DDD refactor.
