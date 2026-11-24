import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { formatBalanceCents } from './money.js';

const CATEGORIES = {
  HOUSING: 'Housing', UTILITIES: 'Utilities', GROCERIES: 'Groceries',
  HOUSEHOLD_SUPPLIES: 'Household supplies', TRANSPORTATION: 'Transportation',
  ENTERTAINMENT: 'Entertainment', OTHER: 'Other',
};

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function errorMessage(error) {
  if (error.status === 401) return 'Your session expired. Sign in again to view spending.';
  if (error.status === 403 || error.status === 404) return 'You can no longer view this household’s spending.';
  return 'We could not load monthly spending. Please try again.';
}

export default function MonthlySpending({ household, refreshToken = 0 }) {
  const { request } = useAuth();
  const [month, setMonth] = useState(currentMonth);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setSummary(null);
    async function load() {
      try {
        const result = await request(`/api/households/${encodeURIComponent(household.id)}/expenses/summary?month=${month}`, {
          signal: controller.signal,
        });
        if (!controller.signal.aborted) setSummary(result);
      } catch (failure) {
        if (failure.name !== 'AbortError' && !controller.signal.aborted) setError(errorMessage(failure));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [household.id, month, refreshToken, retryVersion, request]);

  const currencies = summary?.currencies || [];

  return <section className="card monthly-spending" aria-labelledby="monthly-spending-title">
    <div className="monthly-spending-heading">
      <div><p className="eyebrow">YOUR SPENDING AT A GLANCE</p><h2 id="monthly-spending-title">Monthly spending</h2></div>
      <div className="monthly-spending-actions">
        <label htmlFor="spending-month">Month</label>
        <input id="spending-month" type="month" value={month} onChange={event => setMonth(event.target.value)} />
      </div>
    </div>
    {loading ? <p role="status">Loading monthly spending…</p>
      : error ? <div><p className="error" role="alert">{error}</p>
          <button type="button" className="secondary" onClick={() => setRetryVersion(version => version + 1)}>Try again</button></div>
        : currencies.length === 0 ? <p className="muted">No expenses recorded for this month.</p>
          : <div className="monthly-spending-currencies">{currencies.map(currency => <section key={currency.currency} className="monthly-spending-currency">
            <div className="monthly-spending-total"><div><span className="muted">{currency.currency} total</span>
              <strong>{formatBalanceCents(currency.totalCents, currency.currency)}</strong></div>
              <span className="muted">{currency.expenseCount} {currency.expenseCount === 1 ? 'expense' : 'expenses'}</span></div>
            <h3>By category</h3>
            <ul className="monthly-spending-categories">{currency.categories.map(category => {
              const percent = currency.totalCents ? Math.round(category.totalCents / currency.totalCents * 100) : 0;
              return <li key={category.category}>
                <div className="monthly-spending-category-heading"><span>{CATEGORIES[category.category] || category.category}</span>
                  <strong>{formatBalanceCents(category.totalCents, currency.currency)}</strong></div>
                <div className="monthly-spending-track" aria-hidden="true"><span style={{ width: `${percent}%` }} /></div>
              </li>;
            })}</ul>
          </section>)}</div>}
    <p className="field-help">Totals include all household expenses, not only your share.</p>
  </section>;
}
