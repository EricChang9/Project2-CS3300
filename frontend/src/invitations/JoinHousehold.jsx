import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { useHouseholds } from '../households/HouseholdProvider.jsx';

export default function JoinHousehold() {
  const { request } = useAuth();
  const { refresh } = useHouseholds();
  const [code, setCode] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const alive = useRef(false);
  const controller = useRef(null);
  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; controller.current?.abort(); };
  }, []);

  async function submit(event) {
    event.preventDefault();
    if (pending) return;
    setError(''); setNotice('');
    if (!code.trim()) { setError('Enter the invitation code from your housemate.'); return; }
    setPending(true);
    controller.current = new AbortController();
    try {
      const result = await request('/api/invitations/redeem', { method: 'POST',
        body: JSON.stringify({ code: code.trim() }), signal: controller.current.signal });
      if (!result.householdId || !['joined', 'already_member'].includes(result.status)) throw new Error('Unknown join result');
      const homes = await refresh({ selectId: result.householdId });
      if (!alive.current) return;
      const name = homes?.find(home => home.id === result.householdId)?.name;
      setCode('');
      setNotice(result.status === 'already_member'
        ? `You’re already a member of ${name || 'this household'}.${name ? '' : ' Refresh households to open it.'}`
        : name ? `You joined ${name}. Welcome home!` : 'You joined the household. Refresh households to open it.');
    } catch (failure) {
      if (!alive.current || failure.name === 'AbortError') return;
      if (failure.status === 410) setError('This invitation code has expired. Ask a housemate for a new one.');
      else if ([400, 404].includes(failure.status)) setError('This invitation code is invalid. Check it and try again.');
      else if (failure.status === 409) {
        setNotice('You’re already a member. Refresh your households to open it.');
        await refresh();
      } else setError('We could not confirm the invitation. Refresh your households before trying again.');
    } finally { if (alive.current) setPending(false); }
  }

  return <section className="card" aria-labelledby="join-title">
    <p className="eyebrow">HAVE AN INVITATION?</p><h2 id="join-title">Join your housemates</h2>
    <p className="muted">Already have a shared space? Enter the code a housemate sent you.</p>
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    <form onSubmit={submit} aria-busy={pending}><fieldset disabled={pending}>
      <label htmlFor="join-code">Your invitation code</label><div className="inline-form">
        <input id="join-code" name="invitationCode" autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={256} required value={code} onChange={e => setCode(e.target.value)} placeholder="Paste your code" />
        <button type="submit" className="primary">{pending ? 'Joining…' : 'Join household'}</button>
      </div></fieldset></form>
  </section>;
}
