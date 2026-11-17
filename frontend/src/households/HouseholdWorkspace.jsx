import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { useHouseholds } from './HouseholdProvider.jsx';

function CreateHousehold() {
  const { create } = useHouseholds();
  const [name, setName] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function submit(event) {
    event.preventDefault();
    if (pending) return;
    setError(''); setNotice('');
    if (!name.trim()) { setError('Give your household a name.'); return; }
    setPending(true);
    try {
      const created = await create(name.trim());
      if (created) { setName(''); setNotice(`${created.name} was created.`); }
    } catch (failure) {
      if (failure.name !== 'AbortError') setError(failure.status === 400
        ? 'Use a household name between 1 and 80 characters.' : 'We could not create the household. Check your households before trying again.');
    } finally { setPending(false); }
  }

  return <section className="card" aria-labelledby="create-household-title">
    <p className="eyebrow">A PLACE FOR YOUR PEOPLE</p><h2 id="create-household-title">Create a household</h2>
    <p className="muted">Give your shared space a name. You can add housemates with an invitation later.</p>
    {error && <p role="alert" className="error">{error}</p>}
    {notice && <p role="status" className="notice">{notice}</p>}
    <form onSubmit={submit} aria-busy={pending}><fieldset disabled={pending}>
      <label htmlFor="household-name">Household name</label>
      <div className="inline-form"><input id="household-name" name="householdName" maxLength={80} required placeholder="e.g. The Maple House" value={name} onChange={e => setName(e.target.value)} />
        <button className="primary" type="submit">{pending ? 'Creating…' : 'Create household'}</button></div>
    </fieldset></form>
  </section>;
}

export default function HouseholdWorkspace() {
  const { user } = useAuth();
  const { households, selectedHousehold, selectedId, select, loading, error, refresh } = useHouseholds();
  return <div className="workspace">
    <div className="workspace-heading"><div><p className="eyebrow">YOUR SHARED SPACE</p>
      <h1>Welcome, {user.displayName || user.email || 'housemate'}.</h1><p className="muted">A little less admin. A little more feeling at home.</p></div>
      <button className="secondary" onClick={() => refresh()} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh households'}</button>
    </div>
    {error && <div className="error" role="alert">{error} <button className="text-button" onClick={() => refresh()} disabled={loading}>Try again</button></div>}
    <div className="workspace-grid"><aside className="card household-sidebar" aria-label="Your households">
      <p className="eyebrow">HOME BASE</p><h2>Your households</h2>
      {loading && households.length === 0 ? <p role="status">Loading your households…</p>
        : households.length === 0 ? <p className="muted">No households yet. Create your first shared space to get started.</p>
        : <><label htmlFor="household-selection">Current household</label>
          <select id="household-selection" value={selectedId} onChange={e => select(e.target.value)}>{households.map(home => <option key={home.id} value={home.id}>{home.name}</option>)}</select>
          <p className="field-help">You belong to {households.length} {households.length === 1 ? 'household' : 'households'}.</p></>}
      <div className="sidebar-note"><span aria-hidden="true">⌂</span><p>Shared life works better when everyone is on the same page.</p></div>
    </aside><div className="workspace-content">
      {selectedHousehold ? <section className="card member-card" key={selectedHousehold.id} aria-labelledby="current-home-title">
          <p className="eyebrow">CURRENT HOUSEHOLD</p><h2 id="current-home-title">{selectedHousehold.name}</h2>
          <h3>Housemates <span className="count-badge">{selectedHousehold.members.length}</span></h3>
          <ul className="member-list">{selectedHousehold.members.map((member, index) => <li key={member.uid}>
            <span className="avatar" aria-hidden="true">{member.uid === user.uid ? 'Y' : index + 1}</span>
            <div><strong>{member.uid === user.uid ? 'You' : member.displayName || member.email || 'Housemate'}</strong><span className="member-id">{member.uid}</span></div>
            {member.uid === selectedHousehold.createdBy && <span className="tag">Created this home</span>}
          </li>)}</ul>
        </section> : !loading && !error && <section className="empty-home"><div className="empty-icon" aria-hidden="true">⌂</div><h2>Your home starts here.</h2><p className="muted">Create a household and bring your shared space together.</p></section>}
      <CreateHousehold />
    </div></div>
  </div>;
}
