import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { formatCents } from './money.js';

const CATEGORY_LABELS = {
  HOUSING: 'Housing', UTILITIES: 'Utilities', GROCERIES: 'Groceries',
  HOUSEHOLD_SUPPLIES: 'Household supplies', TRANSPORTATION: 'Transportation',
  ENTERTAINMENT: 'Entertainment', OTHER: 'Other',
};

function formatDate(value) {
  if (!value) return 'Date unavailable';
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return value;
  return new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
    .format(new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
}

function messageFor(error, context) {
  if (error.status === 401) return 'Your session expired. Sign in again to view expenses.';
  if (error.status === 403) return 'You are not allowed to view these expenses.';
  if (error.status === 404 && context === 'detail') return 'This expense was not found in this household.';
  if (error.status === 404) return 'This household is no longer available. Refresh your households and try again.';
  return context === 'detail' ? 'We could not load this expense. Please try again.'
    : 'We could not load household expenses. Please try again.';
}

function memberName(member, user) {
  return member?.username || member?.displayName || member?.email?.split('@')[0]
    || (member?.uid === user.uid ? user.displayName || user.email?.split('@')[0] : null)
    || member?.uid || 'Unknown member';
}

function ExpenseFields({ expense, household, user, detail = false }) {
  const members = household.members || [];
  const payer = members.find(member => member.uid === expense.payerId);
  const shares = expense.sharesCents || {};
  const participantIds = expense.participantIds || Object.keys(shares);
  return <>
    <dl className={detail ? 'expense-detail-fields' : 'expense-summary-fields'}>
      <div><dt>Date</dt><dd>{formatDate(expense.expenseDate)}</dd></div>
      <div><dt>Category</dt><dd>{CATEGORY_LABELS[expense.category] || expense.category || 'Other'}</dd></div>
      <div><dt>Paid by</dt><dd>{memberName(payer, user)}</dd></div>
      <div><dt>Amount</dt><dd>{formatCents(expense.amountCents)}</dd></div>
      <div><dt>Split</dt><dd>{expense.splitType === 'CUSTOM' ? 'Custom shares' : 'Equal shares'}</dd></div>
    </dl>
    {detail && <section className="expense-allocations" aria-labelledby="expense-allocations-title">
      <h3 id="expense-allocations-title">Participant shares</h3>
      {participantIds.length ? <ul>{participantIds.map(uid => {
        const member = members.find(item => item.uid === uid);
        const share = shares[uid];
        return <li key={uid}><span>{memberName(member, user)}</span>
          <strong>{Number.isSafeInteger(share) ? formatCents(share) : 'Share unavailable'}</strong></li>;
      })}</ul> : <p className="muted">No participant shares were recorded.</p>}
    </section>}
  </>;
}

export default function ExpenseHistory({ household, refreshToken = 0 }) {
  const { user, request } = useAuth();
  const [items, setItems] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const [detailId, setDetailId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailVersion, setDetailVersion] = useState(0);
  const [reloadVersion, setReloadVersion] = useState(0);
  const listGeneration = useRef(0);
  const listController = useRef(null);

  const refresh = useCallback(() => {
    listController.current?.abort();
    setDetailId(null);
    setDetail(null);
    setReloadVersion(version => version + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listController.current = controller;
    const generation = ++listGeneration.current;
    setItems([]);
    setNextCursor(null);
    setError('');
    setLoading(true);
    setLoadingMore(false);
    async function load() {
      try {
        const page = await request(`/api/households/${encodeURIComponent(household.id)}/expenses?limit=20`, { signal: controller.signal });
        if (generation !== listGeneration.current) return;
        setItems(Array.isArray(page.items) ? page.items : []);
        setNextCursor(page.nextCursor || null);
      } catch (failure) {
        if (failure.name !== 'AbortError' && generation === listGeneration.current) setError(messageFor(failure, 'history'));
      } finally {
        if (generation === listGeneration.current) setLoading(false);
      }
    }
    load();
    return () => { controller.abort(); if (listController.current === controller) listController.current = null; };
  }, [household.id, reloadVersion, refreshToken, request]);

  useEffect(() => {
    if (!detailId) return undefined;
    const controller = new AbortController();
    setDetail(null);
    setDetailError('');
    setDetailLoading(true);
    async function load() {
      try {
        const expense = await request(`/api/households/${encodeURIComponent(household.id)}/expenses/${encodeURIComponent(detailId)}`, { signal: controller.signal });
        if (!controller.signal.aborted) setDetail(expense);
      } catch (failure) {
        if (failure.name !== 'AbortError' && !controller.signal.aborted) setDetailError(messageFor(failure, 'detail'));
      } finally {
        if (!controller.signal.aborted) setDetailLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [detailId, detailVersion, household.id, request]);

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    const generation = listGeneration.current;
    const controller = new AbortController();
    setLoadingMore(true);
    setError('');
    try {
      const query = new URLSearchParams({ limit: '20', cursor: nextCursor });
      const page = await request(`/api/households/${encodeURIComponent(household.id)}/expenses?${query}`, { signal: controller.signal });
      if (generation !== listGeneration.current) return;
      setItems(previous => [...previous, ...(Array.isArray(page.items) ? page.items : [])]);
      setNextCursor(page.nextCursor || null);
    } catch (failure) {
      if (failure.name !== 'AbortError' && generation === listGeneration.current) setError(messageFor(failure, 'history'));
    } finally {
      if (generation === listGeneration.current) setLoadingMore(false);
    }
  }

  return <section className="card expense-history" aria-labelledby="expense-history-title">
    <div className="expense-history-heading">
      <div><p className="eyebrow">HOUSEHOLD SPENDING</p><h2 id="expense-history-title">Expense history</h2></div>
      <button type="button" className="secondary" onClick={refresh} disabled={loading}>Refresh expenses</button>
    </div>
    {detailId ? <div className="expense-detail" aria-live="polite">
      <button type="button" className="text-button" onClick={() => setDetailId(null)}>← Back to expenses</button>
      {detailLoading ? <p role="status">Loading expense…</p>
        : detailError ? <div role="alert" className="error">{detailError} <button type="button" className="text-button" onClick={() => setDetailVersion(version => version + 1)}>Try again</button></div>
          : detail && <><h3>{detail.description}</h3><ExpenseFields expense={detail} household={household} user={user} detail /></>}
    </div> : <>
      {error && <p role="alert" className="error">{error}</p>}
      {loading ? <p role="status">Loading expenses…</p>
        : items.length === 0 ? error ? <button type="button" className="secondary" onClick={refresh}>Try again</button>
          : <p className="muted">No expenses have been recorded for this household yet.</p>
          : <ul className="expense-list">{items.map(expense => <li key={expense.id}>
              <button type="button" className="expense-list-item" onClick={() => setDetailId(expense.id)} aria-label={`View ${expense.description}`}>
                <span className="expense-list-title"><strong>{expense.description}</strong><span>{formatCents(expense.amountCents)}</span></span>
                <ExpenseFields expense={expense} household={household} user={user} />
                <span className="expense-list-open">View details →</span>
              </button>
            </li>)}</ul>}
      {!loading && nextCursor && <button type="button" className="secondary" onClick={loadMore} disabled={loadingMore}>
        {loadingMore ? 'Loading more…' : error ? 'Try loading more expenses' : 'Load more expenses'}
      </button>}
    </>}
  </section>;
}
