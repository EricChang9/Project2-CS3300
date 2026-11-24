export function paymentRequestsPath(householdId) {
  return `/api/households/${encodeURIComponent(householdId)}/payment-requests`;
}

export function paymentRequestPath(householdId, requestId) {
  return `${paymentRequestsPath(householdId)}/${encodeURIComponent(requestId)}`;
}

export function todayLocal(now = new Date()) {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function validDueDate(value, now = new Date()) {
  if (!value) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
    && value >= todayLocal(now);
}

export function requestErrorMessage(error, action = 'load') {
  if (error.status === 401) return 'Your session expired. Sign in again.';
  if (error.status === 403) return 'You are not allowed to manage this payment request.';
  if (error.status === 404) return 'This household, expense, or share is no longer available.';
  if (error.status === 409) return 'An active request already exists for this share.';
  if (error.status === 400) return 'This share or due date is no longer valid. Check the details and try again.';
  return action === 'create' ? 'We could not create the payment request. Please try again.'
    : 'We could not load payment requests. Please try again.';
}
