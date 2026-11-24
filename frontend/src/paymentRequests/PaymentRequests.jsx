import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { formatBalanceCents } from '../expenses/money.js';
import { paymentRequestPath, requestErrorMessage } from './api.js';

function memberName(household, uid) {
  const member = household.members?.find(item => item.uid === uid);
  return member?.username || member?.displayName || member?.email?.split('@')[0] || uid || 'Unknown member';
}

function expenseName(item) {
  return item.expenseDescription || item.expense?.description || item.expenseId || 'Unknown expense';
}

function statusName(value) {
  return value === 'PAID' ? 'Paid' : value === 'OUTSTANDING' ? 'Outstanding' : value || 'Unknown';
}

function RequestFields({ item, household }) {
  return <dl className="payment-request-fields">
    <div><dt>Creditor</dt><dd>{memberName(household, item.creditorId)}</dd></div>
    <div><dt>Debtor</dt><dd>{memberName(household, item.debtorId)}</dd></div>
    <div><dt>Amount</dt><dd>{formatBalanceCents(item.amountCents, item.currency)}</dd></div>
    <div><dt>Expense</dt><dd>{expenseName(item)}</dd></div>
    <div><dt>Due date</dt><dd>{item.dueDate || 'No due date'}</dd></div>
    <div><dt>Status</dt><dd>{statusName(item.status)}</dd></div>
  </dl>;
}

function RequestList({ title, items, household, onSelect }) {
  return <section className="payment-request-group" aria-label={title}>
    <h3>{title}</h3>
    {items.length === 0 ? <p className="muted">No {title.toLowerCase()} yet.</p>
      : <ul>{items.map(item => <li key={item.id}>
        <button type="button" className="payment-request-item" onClick={() => onSelect(item.id)}
          aria-label={`View request ${item.id}`}>
          <span><strong>{expenseName(item)}</strong> <span className="muted">{statusName(item.status)}</span></span>
          <span>{formatBalanceCents(item.amountCents, item.currency)}</span>
        </button>
      </li>)}</ul>}
  </section>;
}

export default function PaymentRequests({ household, items = [], loading = false, error = '', onRefresh }) {
  const { user, request } = useAuth();
  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailVersion, setDetailVersion] = useState(0);

  useEffect(() => {
    if (!selectedId) return undefined;
    const controller = new AbortController();
    setDetail(null);
    setDetailError('');
    setDetailLoading(true);
    async function load() {
      try {
        const result = await request(paymentRequestPath(household.id, selectedId), { signal: controller.signal });
        if (!controller.signal.aborted) setDetail(result);
      } catch (failure) {
        if (failure.name !== 'AbortError' && !controller.signal.aborted) setDetailError(requestErrorMessage(failure));
      } finally {
        if (!controller.signal.aborted) setDetailLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [selectedId, detailVersion, household.id, request]);

  const sent = items.filter(item => item.creditorId === user.uid);
  const received = items.filter(item => item.debtorId === user.uid);
  const selectedSummary = items.find(item => item.id === selectedId);

  function refresh() {
    if (selectedId) setDetailVersion(value => value + 1);
    onRefresh?.();
  }

  return <section className="card payment-requests" aria-labelledby="payment-requests-title">
    <div className="payment-requests-heading">
      <div><p className="eyebrow">KEEP TRACK</p><h2 id="payment-requests-title">Payment requests</h2></div>
      <button type="button" className="secondary" onClick={refresh} disabled={loading}>Refresh requests</button>
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    {loading ? <p role="status">Loading payment requests…</p>
      : error ? <button type="button" className="secondary" onClick={refresh}>Try again</button>
        : selectedId ? <div className="payment-request-detail">
          <button type="button" className="text-button" onClick={() => setSelectedId(null)}>← Back to requests</button>
          {detailLoading ? <p role="status">Loading request…</p>
            : detailError ? <div><p role="alert" className="error">{detailError}</p>
              <button type="button" className="secondary" onClick={() => setDetailVersion(value => value + 1)}>Try again</button></div>
              : detail && <><h3>{expenseName(detail)}</h3>
                <RequestFields item={{ ...selectedSummary, ...detail }} household={household} /></>}
        </div>
          : <div className="payment-request-groups">
            <RequestList title="Sent requests" items={sent} household={household} onSelect={setSelectedId} />
            <RequestList title="Received requests" items={received} household={household} onSelect={setSelectedId} />
          </div>}
  </section>;
}
