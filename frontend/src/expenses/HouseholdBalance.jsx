import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { formatBalanceCents } from './money.js';

function errorMessage(error) {
  if (error.status === 401) return 'Your session expired. Sign in again to view balances.';
  if (error.status === 403 || error.status === 404) return 'You can no longer view this household’s balances.';
  return 'We could not load household balances. Please try again.';
}

function memberName(member, user) {
  return member?.username || member?.displayName || member?.email?.split('@')[0]
    || (member?.uid === user.uid ? user.displayName || user.email?.split('@')[0] : null)
    || member?.uid || 'Unknown member';
}

export default function HouseholdBalance({ household, refreshToken = 0 }) {
  const { user, request } = useAuth();
  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryVersion, setRetryVersion] = useState(0);
  const [paymentVersion, setPaymentVersion] = useState(0);
  const [paymentLoading, setPaymentLoading] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [paymentNotice, setPaymentNotice] = useState('');
  const paymentRequest = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    setBalance(null);
    setError('');
    setLoading(true);
    async function load() {
      try {
        const result = await request(`/api/households/${encodeURIComponent(household.id)}/balances`, {
          signal: controller.signal,
        });
        if (!controller.signal.aborted) setBalance(result);
      } catch (failure) {
        if (failure.name !== 'AbortError' && !controller.signal.aborted) setError(errorMessage(failure));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [household.id, refreshToken, request, retryVersion, paymentVersion]);

  async function recordPayment(debt) {
    if (paymentLoading) return;
    const key = `${household.id}/${debt.debtorId}/${debt.creditorId}/${debt.currency}/${debt.amountCents}`;
    if (paymentRequest.current?.key !== key) {
      paymentRequest.current = { key, requestId: crypto.randomUUID() };
    }
    setPaymentLoading(key);
    setPaymentError('');
    setPaymentNotice('');
    try {
      await request(`/api/households/${encodeURIComponent(household.id)}/settlements`, {
        method: 'POST',
        body: JSON.stringify({ toUid: debt.creditorId, currency: debt.currency,
          amountCents: debt.amountCents, requestId: paymentRequest.current.requestId }),
      });
      paymentRequest.current = null;
      setPaymentNotice('Simulated payment recorded. No money was transferred.');
      setPaymentVersion(version => version + 1);
    } catch (failure) {
      setPaymentError(failure.status === 409 ? 'The balance has changed. Refresh balances before trying again.'
        : 'We could not record the simulated payment. Please try again.');
    } finally {
      setPaymentLoading('');
    }
  }

  const members = household.members || [];
  const currentBalance = balance?.members?.find(member => member.memberId === user.uid);
  const personalByCurrency = currentBalance?.byCurrency || {};
  const currencies = [...new Set([
    ...(balance?.totals || []).map(total => total.currency),
    ...Object.keys(personalByCurrency),
  ])].sort();
  const debts = balance?.debts || [];

  return <section className="card household-balance" aria-labelledby="household-balance-title">
    <div className="balance-heading">
      <div><p className="eyebrow">WHERE THINGS STAND</p><h2 id="household-balance-title">Household balances</h2></div>
      <button type="button" className="secondary" onClick={() => setRetryVersion(version => version + 1)} disabled={loading}>
        {loading ? 'Refreshing…' : 'Refresh balances'}
      </button>
    </div>
    {loading ? <p role="status">Loading balances…</p>
      : error ? <div><p role="alert" className="error">{error}</p>
        <button type="button" className="secondary" onClick={() => setRetryVersion(version => version + 1)}>Try again</button></div>
        : !currentBalance ? <p role="alert" className="error">Your balance is unavailable for this household.</p>
          : <>
            <section className="personal-balance" aria-labelledby="personal-balance-title">
              <h3 id="personal-balance-title">Your amounts</h3>
              {currencies.length === 0 ? <p className="muted">You’re settled up. No balances are outstanding.</p>
                : <div className="personal-balance-list">{currencies.map(currency => {
                  const totals = personalByCurrency[currency] || { owesCents: 0, owedCents: 0, netBalanceCents: 0 };
                  const net = totals.netBalanceCents || 0;
                  return <article className="personal-balance-currency" key={currency}>
                    <h4>{currency}</h4>
                    <dl>
                      <div><dt>You owe</dt><dd className="balance-owes">{formatBalanceCents(totals.owesCents || 0, currency)}</dd></div>
                      <div><dt>Owed to you</dt><dd className="balance-owed">{formatBalanceCents(totals.owedCents || 0, currency)}</dd></div>
                    </dl>
                    <p className={`balance-net${net === 0 ? ' balance-net-settled' : ''}`}>
                      {net > 0 ? `You’re owed ${formatBalanceCents(net, currency)}`
                        : net < 0 ? `You owe ${formatBalanceCents(Math.abs(net), currency)}` : 'You’re settled up'}
                    </p>
                  </article>;
                })}</div>}
            </section>
            <section className="directed-debts" aria-labelledby="directed-debts-title">
              <h3 id="directed-debts-title">Outstanding between housemates</h3>
              {paymentError && <p className="error" role="alert">{paymentError}</p>}
              {paymentNotice && <p className="notice" role="status">{paymentNotice}</p>}
              {debts.length === 0 ? <p className="notice balance-settled" role="status">Everyone is settled up.</p>
                : <ul>{debts.map((debt, index) => {
                  const debtor = members.find(member => member.uid === debt.debtorId);
                  const creditor = members.find(member => member.uid === debt.creditorId);
                  return <li key={`${debt.currency}-${debt.debtorId}-${debt.creditorId}-${index}`}>
                    <span><strong>{memberName(debtor, user)}</strong> owes <strong>{memberName(creditor, user)}</strong></span>
                    <strong>{formatBalanceCents(debt.amountCents, debt.currency)}</strong>
                    {debt.debtorId === user.uid && <button type="button" className="secondary"
                      disabled={Boolean(paymentLoading) || loading} onClick={() => recordPayment(debt)}>
                      {paymentLoading === `${household.id}/${debt.debtorId}/${debt.creditorId}/${debt.currency}/${debt.amountCents}`
                        ? 'Recording…' : 'Mark paid (demo)'}
                    </button>}
                  </li>;
                })}</ul>}
              {debts.some(debt => debt.debtorId === user.uid) &&
                <p className="field-help">Demo only. This records a settlement without transferring money.</p>}
            </section>
          </>}
  </section>;
}
