/** A fresh key for one logical reservation attempt; retries of that attempt reuse it (PRD 6.10). */
export const newIdempotencyKey = () => crypto.randomUUID();
