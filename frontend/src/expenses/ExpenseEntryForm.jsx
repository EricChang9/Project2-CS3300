import { useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { calculateEqualShares, formatCents, parseMoneyToCents } from './money.js';

const CATEGORIES = [
  ['HOUSING', 'Housing'],
  ['UTILITIES', 'Utilities'],
  ['GROCERIES', 'Groceries'],
  ['HOUSEHOLD_SUPPLIES', 'Household supplies'],
  ['TRANSPORTATION', 'Transportation'],
  ['ENTERTAINMENT', 'Entertainment'],
  ['OTHER', 'Other'],
];

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function displayName(member, currentUser) {
  return member.username || member.displayName || member.email?.split('@')[0]
    || (member.uid === currentUser.uid ? currentUser.displayName || currentUser.email?.split('@')[0] : null)
    || member.uid;
}

function failureMessage(error) {
  if (error.status === 400) return 'Some expense details are invalid. Check the amount, date, and shares.';
  if (error.status === 401) return 'Your session expired. Sign in again to save this expense.';
  if (error.status === 404) return 'You may no longer be a member of this household. Refresh your households and try again.';
  if (error.status === 503) return 'Expense saving is temporarily unavailable. Try again in a moment.';
  return 'We could not save this expense. Please try again.';
}

export default function ExpenseEntryForm({ household }) {
  const { user, request } = useAuth();
  const members = useMemo(() => household.members || [], [household.members]);
  const memberIds = useMemo(() => members.map(member => member.uid), [members]);
  const currentMemberId = memberIds.includes(user.uid) ? user.uid : memberIds[0] || '';
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [payerId, setPayerId] = useState(currentMemberId);
  const [participantIds, setParticipantIds] = useState(memberIds);
  const [category, setCategory] = useState('OTHER');
  const [expenseDate, setExpenseDate] = useState(today);
  const [splitType, setSplitType] = useState('EQUAL');
  const [customAmounts, setCustomAmounts] = useState({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [savedExpense, setSavedExpense] = useState(null);
  const [validationError, setValidationError] = useState('');

  const amountCents = parseMoneyToCents(amount);
  const equalShares = calculateEqualShares(amountCents, participantIds);
  const customAllocations = {};
  let customTotal = 0;
  let customAmountsValid = true;
  for (const uid of participantIds) {
    const cents = parseMoneyToCents(customAmounts[uid]);
    if (cents === null) customAmountsValid = false;
    else {
      customTotal += cents;
      customAllocations[uid] = cents;
      if (!Number.isSafeInteger(customTotal)) customAmountsValid = false;
    }
  }

  function changed(setter) {
    return event => {
      setter(event.target.value);
      setError('');
      setSavedExpense(null);
      setValidationError('');
    };
  }

  function toggleParticipant(uid) {
    setParticipantIds(previous => {
      const next = new Set(previous);
      if (next.has(uid)) next.delete(uid);
      else next.add(uid);
      return memberIds.filter(id => next.has(id));
    });
    setError('');
    setSavedExpense(null);
    setValidationError('');
  }

  function resetForm() {
    setDescription('');
    setAmount('');
    setPayerId(currentMemberId);
    setParticipantIds(memberIds);
    setCategory('OTHER');
    setExpenseDate(today());
    setSplitType('EQUAL');
    setCustomAmounts({});
  }

  async function submit(event) {
    event.preventDefault();
    if (pending) return;
    setError('');
    setSavedExpense(null);
    setValidationError('');

    if (!description.trim()) return setValidationError('Enter a description for this expense.');
    if (amountCents === null || amountCents <= 0) return setValidationError('Enter a positive amount with up to two decimal places.');
    if (!memberIds.includes(payerId)) return setValidationError('Choose a payer from this household.');
    if (participantIds.length === 0) return setValidationError('Choose at least one participant.');
    if (participantIds.some(uid => !memberIds.includes(uid))) return setValidationError('Participants must be current household members.');
    if (!expenseDate) return setValidationError('Choose the date of this expense.');
    if (splitType === 'CUSTOM' && (!customAmountsValid || customTotal !== amountCents)) {
      return setValidationError(`Custom shares must add up to ${formatCents(amountCents)}.`);
    }

    const body = {
      description: description.trim(),
      amountCents,
      currency: 'USD',
      payerId,
      participantIds,
      expenseDate,
      category,
      splitType,
      ...(splitType === 'CUSTOM' ? { customAllocations } : {}),
    };
    setPending(true);
    try {
      const created = await request(`/api/households/${encodeURIComponent(household.id)}/expenses`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      setSavedExpense(created);
      resetForm();
    } catch (failure) {
      if (failure.name !== 'AbortError') setError(failureMessage(failure));
    } finally {
      setPending(false);
    }
  }

  const savedShares = savedExpense?.sharesCents || {};

  return <section className="card expense-entry" aria-labelledby="expense-entry-title">
    <p className="eyebrow">SHARE THE EVERYDAY</p>
    <h2 id="expense-entry-title">Add an expense</h2>
    <p className="muted">Record what you paid and decide how your housemates share it.</p>
    <p className="field-help">Amounts are in USD. Shares are saved in cents.</p>
    {error && <p role="alert" className="error">{error}</p>}
    {validationError && <p role="alert" className="error">{validationError}</p>}
    {savedExpense && <div className="notice expense-saved" role="status" aria-label="Expense saved">
      <strong>{savedExpense.description || 'Expense'} saved.</strong>
      <span>Final shares:</span>
      <ul>{Object.entries(savedShares).map(([uid, cents]) => {
        const member = members.find(item => item.uid === uid);
        return <li key={uid}>{member ? displayName(member, user) : uid}: {formatCents(cents)}</li>;
      })}</ul>
    </div>}
    <form onSubmit={submit} aria-busy={pending}>
      <fieldset disabled={pending}>
        <label htmlFor="expense-description">Description</label>
        <input id="expense-description" name="description" maxLength={200} required value={description}
          onChange={changed(setDescription)} placeholder="e.g. Electricity bill" />

        <label htmlFor="expense-amount">Amount (USD)</label>
        <input id="expense-amount" name="amount" type="text" inputMode="decimal" autoComplete="off"
          required value={amount} onChange={changed(setAmount)} placeholder="0.00" />

        <label htmlFor="expense-payer">Paid by</label>
        <select id="expense-payer" name="payerId" required value={payerId} onChange={changed(setPayerId)}>
          {members.map(member => <option key={member.uid} value={member.uid}>{displayName(member, user)}</option>)}
        </select>

        <label htmlFor="expense-category">Category</label>
        <select id="expense-category" name="category" value={category} onChange={changed(setCategory)}>
          {CATEGORIES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>

        <label htmlFor="expense-date">Expense date</label>
        <input id="expense-date" name="expenseDate" type="date" required value={expenseDate}
          onChange={changed(setExpenseDate)} />

        <fieldset className="split-options">
          <legend>Split type</legend>
          <label><input type="radio" name="splitType" value="EQUAL" checked={splitType === 'EQUAL'}
            onChange={changed(setSplitType)} /> Split equally</label>
          <label><input type="radio" name="splitType" value="CUSTOM" checked={splitType === 'CUSTOM'}
            onChange={changed(setSplitType)} /> Set custom shares</label>
        </fieldset>

        <fieldset className="participant-options">
          <legend>Who shares this expense?</legend>
          {members.map(member => {
            const name = displayName(member, user);
            const included = participantIds.includes(member.uid);
            const share = splitType === 'EQUAL' ? equalShares[member.uid] : undefined;
            return <div className="participant-row" key={member.uid}>
              <label className="participant-choice">
                <input type="checkbox" checked={included} onChange={() => toggleParticipant(member.uid)} />
                <span>{name}</span>
              </label>
              {included && splitType === 'CUSTOM' && <div className="custom-share">
                <label htmlFor={`expense-share-${member.uid}`}>Share for {name} (USD)</label>
                <input id={`expense-share-${member.uid}`} name={`customShare-${member.uid}`}
                  type="text" inputMode="decimal" autoComplete="off" placeholder="0.00"
                  value={customAmounts[member.uid] || ''}
                  onChange={event => {
                    setCustomAmounts(previous => ({ ...previous, [member.uid]: event.target.value }));
                    setError(''); setSavedExpense(null); setValidationError('');
                  }} />
              </div>}
              {included && splitType === 'EQUAL' && share !== undefined &&
                <span className="estimated-share">{formatCents(share)}</span>}
            </div>;
          })}
        </fieldset>
        {splitType === 'CUSTOM' && <p className={`share-total${customAmountsValid && customTotal === amountCents ? ' share-total-valid' : ''}`}>
          Custom shares total {formatCents(customTotal)}{amountCents !== null ? ` of ${formatCents(amountCents)}` : ''}.
        </p>}
      </fieldset>
      <button className="primary full-width" type="submit" disabled={pending || members.length === 0}>
        {pending ? 'Saving expense…' : 'Save expense'}
      </button>
    </form>
  </section>;
}
