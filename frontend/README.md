# FlashSaleEngine frontend

React + Vite + TypeScript + Tailwind CSS. Every request goes through the API Gateway; the browser never calls a service directly.

## Run

Requires Node.js 20.19+ (LTS recommended).

```bash
npm install
npm run dev      # http://localhost:3000 (the origin the Gateway's CORS rule allows)
npm run build    # type-check and production build into dist/
```

## Data modes

Set `VITE_DATA_MODE` in `.env.local` (see `.env.example`):

- `demo` (default): fictional sample events from `src/demo/`, labelled on every page. No backend needed.
- `api`: real data from the API Gateway at `VITE_API_BASE_URL` (connected in Stage 5).

Both implement `src/data/CatalogSource.ts`, so demo and real data are never mixed.

## Event images

The backend stores no images. Each event gets a themed illustration from `src/components/sale/eventArtwork.ts`.
To use real photos, add them to `public/events/` and map event IDs to them in `PHOTOS` in that file.
