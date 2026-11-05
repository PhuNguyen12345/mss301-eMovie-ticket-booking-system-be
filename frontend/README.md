# eMovie Studio demo

An interactive Vietnamese admin preview for showtime schedules and seat-type
pricing. Built with React, TypeScript and Vite. Fonts and illustrations are
local; no third-party image requests are required.

```sh
npm ci
npm run dev -- --port 5173
```

Open http://127.0.0.1:5173. The demo includes:

- Cinema, date, room, movie-name and status filters.
- A selected show's Standard / VIP / Couple prices; blank prices fall back to
  the base price. Prices persist across reloads in localStorage.
- Showtime creation with future-date, price and overlap validation.
- Confirmation before cancellation; started shows cannot be cancelled.
- Responsive desktop/mobile layouts, native keyboard-accessible dialogs,
  inline errors, empty states and reduced-motion support.

## Preview scope

This is a **demo using sample data**, not an authenticated admin client. All
changes stay in the current browser. There are no backend writes or login
flows. Sample shows are regenerated each calendar day. Clear the
`emovie-studio-demo-v1` localStorage entry to reset the demo.

The branch starts from `feat/issue-11-showtime-seat-prices`, independently of
the backend refactor branch. FE implementation/API integration can follow
after the visual demo is reviewed. Showtime fields and price request shapes
mirror the existing backend contract; movie/cinema lists are sample fixtures.

## Validation

```sh
npm run build
npx playwright install chromium
npm run test:e2e
```

An already-installed Chromium can be selected with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/absolute/path/to/chrome`.
Browser tests cover filters, saved prices and fallback, overlap rejection,
creation/cancellation, dialog keyboard behavior, mobile width and date navigation.
