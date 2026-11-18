import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';

export default function InvitationShare({ household }) {
  const { request } = useAuth();
  const [invitation, setInvitation] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const alive = useRef(false);
  const controller = useRef(null);
  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; controller.current?.abort(); };
  }, []);

  async function generate() {
    if (pending) return;
    setPending(true); setError(''); setNotice('');
    controller.current = new AbortController();
    try {
      const data = await request(`/api/households/${encodeURIComponent(household.id)}/invitations`,
        { method: 'POST', signal: controller.current.signal });
      if (!data.code || !Number.isFinite(Date.parse(data.expiresAt))) throw new Error('Invalid invitation response');
      if (alive.current) setInvitation(data);
    } catch (failure) {
      if (alive.current && failure.name !== 'AbortError') setError(failure.status === 403
        ? 'You cannot create invitations for this household.' : 'Invitations are unavailable right now. Please try again later.');
    } finally { if (alive.current) setPending(false); }
  }

  async function copy() {
    setError(''); setNotice('');
    if (Date.parse(invitation.expiresAt) <= Date.now()) { setError('This code has expired. Create a new invitation.'); return; }
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(invitation.code);
      if (alive.current) setNotice('Invitation code copied.');
    } catch {
      if (alive.current) setNotice('Select the invitation code and copy it manually.');
    }
  }

  return <section className="card" aria-labelledby="invite-title">
    <p className="eyebrow">BETTER WITH HOUSEMATES</p><h2 id="invite-title">Invite someone home</h2>
    <p className="muted">Share a code so a roommate can join {household.name}. Only send it to people you want in your household.</p>
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {invitation && <div className="invitation-result">
      <label htmlFor="invitation-code">Invitation code</label><div className="inline-form">
        <input id="invitation-code" className="code-input" readOnly value={invitation.code} onFocus={event => event.target.select()} />
        <button type="button" className="secondary" onClick={copy} disabled={pending}>Copy code</button>
      </div><p className="field-help">Expires {new Date(invitation.expiresAt).toLocaleString()}.</p>
    </div>}
    <button type="button" className="secondary spaced-button" onClick={generate} disabled={pending}>
      {pending ? 'Creating code…' : invitation ? 'Create new code' : 'Create invitation code'}
    </button>
  </section>;
}
