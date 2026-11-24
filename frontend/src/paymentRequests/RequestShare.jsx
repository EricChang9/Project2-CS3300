import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { formatBalanceCents } from '../expenses/money.js';
import { paymentRequestsPath, requestErrorMessage, todayLocal, validDueDate } from './api.js';

function memberName(household, uid) {
  const member = household.members?.find(item => item.uid === uid);
  return member?.username || member?.displayName || member?.email?.split('@')[0] || uid;
}

export default function RequestShare({ expense, household, requests = [], loading = false, onCreated }) {
  const { user, request } = useAuth();
  const [selected, setSelected] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [created, setCreated] = useState([]);
  const shares = expense.sharesCents || {};
  const eligible = expense.payerId === user.uid && !loading
    ? Object.entries(shares).filter(([uid, cents]) => uid !== user.uid
      && household.members?.some(member => member.uid === uid)
      && Number.isSafeInteger(cents) && cents > 0 && !created.includes(uid)
      && !requests.some(item => item.expenseId === expense.id && item.debtorId === uid))
    : [];

  async function submit(event) {
    event.preventDefault();
    if (pending || !eligible.some(([uid]) => uid === selected)) return;
    setError('');
    setNotice('');
    if (!validDueDate(dueDate)) { setError('Choose a valid due date that is not in the past.'); return; }
    setPending(true);
    try {
      await request(paymentRequestsPath(household.id), {
        method: 'POST',
        body: JSON.stringify({ expenseId: expense.id, debtorId: selected, ...(dueDate ? { dueDate } : {}) }),
      });
      setCreated(previous => [...previous, selected]);
      setSelected('');
      setDueDate('');
      setNotice('Payment request sent.');
      onCreated?.();
    } catch (failure) {
      if (failure.name !== 'AbortError') setError(requestErrorMessage(failure, 'create'));
    } finally { setPending(false); }
  }

  if (expense.payerId !== user.uid || (!eligible.length && !selected && !notice && !error)) return null;

  return <section className="expense-request" aria-labelledby={`expense-request-${expense.id}`}>
    <h3 id={`expense-request-${expense.id}`}>Request a share</h3>
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {!selected ? <div className="request-share-actions">{eligible.map(([uid, cents]) =>
      <button className="secondary" type="button" key={uid} disabled={pending}
        onClick={() => { setSelected(uid); setDueDate(''); setError(''); setNotice(''); }}>
        Request {formatBalanceCents(cents, expense.currency)} from {memberName(household, uid)}
      </button>)}</div>
      : <form onSubmit={submit} noValidate>
        <p>Requesting {formatBalanceCents(shares[selected], expense.currency)} from {memberName(household, selected)}</p>
        <label htmlFor={`share-due-${expense.id}`}>Due date (optional)</label>
        <input id={`share-due-${expense.id}`} type="date" min={todayLocal()} value={dueDate}
          onChange={event => setDueDate(event.target.value)} />
        <div className="request-share-actions">
          <button type="submit" className="primary" disabled={pending}>{pending ? 'Sending…' : 'Send request'}</button>
          <button type="button" className="secondary" disabled={pending} onClick={() => { setSelected(''); setError(''); }}>Cancel</button>
        </div>
      </form>}
  </section>;
}
