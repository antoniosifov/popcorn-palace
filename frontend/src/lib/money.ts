/**
 * Prices arrive as JSON numbers with two decimals (22.5 for 22.50). Sums are
 * done in whole cents so that 14.50 + 14.50 + 22.00 is exactly 51.00.
 */

export function toCents(amount: number): number {
  return Math.round(amount * 100);
}

export function sumCents(amounts: number[]): number {
  return amounts.reduce((total, amount) => total + toCents(amount), 0);
}

const formatters = new Map<string, Intl.NumberFormat>();

/** 4400 cents in USD → "$44.00". Always two decimals, with thousands separators. */
export function formatCents(cents: number, currency: string): string {
  let formatter = formatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 });
    formatters.set(currency, formatter);
  }
  return formatter.format(cents / 100);
}

/** 22 in USD → "$22.00". */
export function formatMoney(amount: number, currency: string): string {
  return formatCents(toCents(amount), currency);
}
