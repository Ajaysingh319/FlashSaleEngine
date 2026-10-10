import { config } from "../config/env";

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: config.currency,
  maximumFractionDigits: 0,
});

const dateTime = new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

export const formatMoney = (amount: number) => money.format(amount);
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso));
export const pad2 = (value: number) => String(value).padStart(2, "0");
