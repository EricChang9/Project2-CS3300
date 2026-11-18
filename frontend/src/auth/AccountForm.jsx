import { useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import { authErrorMessage } from './firebase.js';

export default function AccountForm() {
  const { configured, signIn, register, message } = useAuth();
  const [mode, setMode] = useState('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const creating = mode === 'register';

  async function submit(event) {
    event.preventDefault();
    if (pending || !configured) return;
    setError('');
    if (creating && password !== confirmation) { setError('The passwords do not match.'); return; }
    setPending(true);
    try { await (creating ? register : signIn)(email.trim(), password); }
    catch (failure) { setError(authErrorMessage(failure)); }
    finally { setPassword(''); setConfirmation(''); setPending(false); }
  }

  function switchMode() {
    setMode(creating ? 'signin' : 'register'); setError(''); setPassword(''); setConfirmation('');
  }

  return <section className="card account-card" aria-labelledby="account-title">
    <p className="eyebrow">YOUR HOME, IN SYNC</p>
    <h2 id="account-title">{creating ? 'Make yourself at home.' : 'Welcome home.'}</h2>
    <p className="muted">{creating ? 'Create an account to share the everyday.' : 'Sign in to keep shared life running smoothly.'}</p>
    {!configured && <p className="notice">Account access is not available in this environment yet.</p>}
    {message && <p role="alert" className="notice">{message}</p>}
    {error && <p role="alert" className="error" id="account-error">{error}</p>}
    <form onSubmit={submit} aria-busy={pending} aria-describedby={error ? 'account-error' : undefined}>
      <fieldset disabled={pending || !configured}>
        <label htmlFor="email">Email address</label>
        <input id="email" name="email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" />
        <label htmlFor="password">Password</label>
        <input id="password" name="password" type="password" autoComplete={creating ? 'new-password' : 'current-password'} minLength={creating ? 6 : undefined} required value={password} onChange={e => setPassword(e.target.value)} />
        {creating && <><p className="field-help">Use at least 6 characters.</p>
          <label htmlFor="confirmation">Confirm password</label>
          <input id="confirmation" name="confirmation" type="password" autoComplete="new-password" required value={confirmation} onChange={e => setConfirmation(e.target.value)} /></>}
        <button className="primary full-width" type="submit">{pending ? 'Please wait…' : creating ? 'Create account' : 'Sign in'}</button>
      </fieldset>
    </form>
    <p className="form-footer">{creating ? 'Already a housemate?' : 'New around here?'}{' '}
      <button className="text-button" type="button" disabled={pending} onClick={switchMode}>{creating ? 'Sign in' : 'Create an account'}</button></p>
  </section>;
}
