const MONEY_PATTERN = /^(?:\d+(?:\.\d{0,2})?|\.\d{1,2})$/;

/** Parse a typed dollar amount using decimal strings, never binary floating point. */
export function parseMoneyToCents(value) {
  const text = String(value ?? '').trim();
  if (!MONEY_PATTERN.test(text)) return null;
  const [whole = '0', fraction = ''] = text.startsWith('.') ? ['0', text.slice(1)] : text.split('.');
  const cents = Number(whole) * 100 + Number((fraction + '00').slice(0, 2));
  return Number.isSafeInteger(cents) ? cents : null;
}

export function formatCents(cents) {
  const whole = Math.floor(Math.abs(cents) / 100);
  const fraction = String(Math.abs(cents) % 100).padStart(2, '0');
  return `${cents < 0 ? '-' : ''}$${whole}.${fraction}`;
}

/** Format integer minor units without floating-point conversion, retaining the currency code. */
export function formatBalanceCents(cents, currency = 'USD') {
  if (!Number.isSafeInteger(cents)) return 'Amount unavailable';
  const formatted = formatCents(cents);
  return currency === 'USD' ? formatted : `${formatted.replace('$', '')} ${currency}`;
}

export function calculateEqualShares(totalCents, participantIds) {
  if (!Number.isSafeInteger(totalCents) || totalCents < 0 || participantIds.length === 0) return {};
  const base = Math.floor(totalCents / participantIds.length);
  const remainder = totalCents % participantIds.length;
  return Object.fromEntries(participantIds.map((uid, index) => [uid, base + (index < remainder ? 1 : 0)]));
}
