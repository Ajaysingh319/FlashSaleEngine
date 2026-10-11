#!/usr/bin/env node
/**
 * Creates a set of flash sales in the running backend, through the API Gateway, as the admin account:
 * live sales (sale window open now) and upcoming ones, each with ticket types. Catalog provisions their stock
 * in Reservation Service, so the frontend shows real events, prices and inventory immediately.
 *
 * Run (Node 18+, backend running, same admin as infrastructure/.env):
 *   ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/seed-sales.mjs
 * Optional: GATEWAY_URL (default http://localhost:8080). Re-running skips sales that already exist by name.
 */

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";
const HOUR = 3_600_000;
const DAY = 24 * HOUR;

const SALES = [
  {
    name: "Neon Skyline Live: Arena Tour",
    description: "An electro-pop night of lasers, live vocals and a 360° stage. One night only.",
    venue: "Skyline Arena", city: "Mumbai", status: "ON_SALE", saleOpensIn: -HOUR, saleOpenFor: 6 * HOUR, startsIn: 12 * DAY,
    tickets: [{ name: "General", price: 2499, totalQuantity: 300 }, { name: "VIP Floor", price: 7999, totalQuantity: 100 }],
  },
  {
    name: "Monsoon Beats Festival",
    description: "Two stages, twenty artists and a rain-proof open-air garden for a full day of music.",
    venue: "Garden Grounds", city: "Bengaluru", status: "ON_SALE", saleOpensIn: -HOUR, saleOpenFor: 2 * DAY, startsIn: 20 * DAY,
    tickets: [{ name: "Day Pass", price: 1999, totalQuantity: 800 }, { name: "Backstage Pass", price: 5499, totalQuantity: 80 }],
  },
  {
    name: "Champions Cricket Night",
    description: "Floodlit final under the stars: 100 seats, released all at once.",
    venue: "Harbour Stadium", city: "Chennai", status: "ON_SALE", saleOpensIn: -HOUR, saleOpenFor: 12 * HOUR, startsIn: 6 * DAY,
    tickets: [{ name: "Stand", price: 1499, totalQuantity: 100 }],
  },
  {
    name: "Stand-up Saturdays: Comedy Gala",
    description: "Five headliners, one stage and two hours of brand-new material.",
    venue: "The Laugh Loft", city: "Delhi", status: "ON_SALE", saleOpensIn: -HOUR, saleOpenFor: 3 * DAY, startsIn: 9 * DAY,
    tickets: [{ name: "Standard", price: 999, totalQuantity: 150 }, { name: "Front Row", price: 2499, totalQuantity: 30 }],
  },
  {
    name: "Symphony Under the Stars",
    description: "A 60-piece orchestra performs film scores in an open-air amphitheatre.",
    venue: "Hilltop Amphitheatre", city: "Pune", status: "UPCOMING", saleOpensIn: 2 * DAY, saleOpenFor: 3 * DAY, startsIn: 18 * DAY,
    tickets: [{ name: "Lawn", price: 1299, totalQuantity: 400 }],
  },
];

async function call(method, path, token, body) {
  const response = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${path} failed with HTTP ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

function required(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`${name} must be set (the admin account from infrastructure/.env).`);
    process.exit(1);
  }
  return value;
}

async function main() {
  const { accessToken } = await call("POST", "/api/v1/auth/login", null, {
    email: required("ADMIN_EMAIL"),
    password: required("ADMIN_PASSWORD"),
  });
  const now = Date.now();
  const at = (offset) => new Date(now + offset).toISOString();

  for (const sale of SALES) {
    const existing = await call("GET", `/api/v1/events?q=${encodeURIComponent(sale.name)}`, null);
    if (existing.some((event) => event.name === sale.name)) {
      console.log(`skip     ${sale.name} (already exists)`);
      continue;
    }
    const event = await call("POST", "/api/v1/events", accessToken, {
      name: sale.name,
      description: sale.description,
      venue: sale.venue,
      city: sale.city,
      status: sale.status,
      startTime: at(sale.startsIn),
      endTime: at(sale.startsIn + 3 * HOUR),
      saleStartTime: at(sale.saleOpensIn),
      saleEndTime: at(sale.saleOpensIn + sale.saleOpenFor),
    });
    for (const ticket of sale.tickets) {
      await call("POST", `/api/v1/events/${encodeURIComponent(event.id)}/ticket-types`, accessToken, ticket);
    }
    console.log(`created  ${sale.name} -> http://localhost:3000/events/${event.id}`);
  }
  console.log("Done. Open http://localhost:3000 to see the sales.");
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
