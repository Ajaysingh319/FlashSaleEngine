# FlashSaleEngine frontend

React + Vite + TypeScript + Tailwind CSS. Every request goes through the API Gateway; the browser never calls a service directly.

## Run

Requires Node.js 20.19+ (LTS recommended) and, for real data, the backend running (see "See it working end to end" in the root README).

```bash
npm install
npm run dev      # http://localhost:3000 (the origin the Gateway's CORS rule allows)
npm run build    # type-check and production build into dist/
```

## Pages

| Path | Page | Sign-in |
|------|------|---------|
| `/` | Home: featured live sale, live and upcoming sales | No |
| `/sales` | All sales, with search (`?q=`) | No |
| `/events/:id` | Event: ticket types, live stock, quantity, Reserve Now | To reserve |
| `/checkout/:reservationId` | Order and simulated payment, live result | Yes |
| `/reservations`, `/orders` | My Reservations, My Orders | Yes |
| `/login`, `/register` | Sign in, create account | No |

## Data modes

`VITE_DATA_MODE` (see `.env.example`; override in `.env.local`, then restart `npm run dev`):

- `api` (default): real data and sign-in through the API Gateway at `VITE_API_BASE_URL`. If the backend is not running,
  pages say the sales are unavailable; nothing is faked.
- `demo`: built-in sample sales from `src/demo/` for previewing the design without a backend. A small
  "Sample data" chip in the header marks this mode; sign-in is not available in it.

Both implement the contracts in `src/data/` (`CatalogSource`, `ReservationSource`, `OrderSource`), and `src/data/sources.ts`
picks one, so sample and real data are never mixed.

## How it talks to the backend

- `src/api/http.ts`: one Axios client with the Bearer token; a 401 triggers a single shared token refresh and one retry.
- Access token in memory only; refresh token in `sessionStorage` (cleared when the tab closes).
- `src/api/toAppError.ts`: maps every backend error format to one error model, so pages show the right message and action.
- Reservations, orders and payments send an `Idempotency-Key`; retries reuse it, so nothing is ever created twice.

## Event images

The backend stores no images. Each event gets a themed illustration from `src/components/sale/eventArtwork.ts`.
To use real photos, add them to `public/events/` and map event IDs to them in `PHOTOS` in that file.
