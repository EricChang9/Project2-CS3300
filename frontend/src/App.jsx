import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthProvider.jsx';
import AccountForm from './auth/AccountForm.jsx';
import { HouseholdProvider } from './households/HouseholdProvider.jsx';
import HouseholdWorkspace from './households/HouseholdWorkspace.jsx';

function BackendStatus() {
  const [status, setStatus] = useState('Checking backend…');
  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/health', { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error();
      const health = await response.json();
      setStatus(health.status === 'UP' ? 'Backend connected' : 'Backend unavailable');
    }).catch(error => { if (error.name !== 'AbortError') setStatus('Backend unavailable. Start the Spring Boot server.'); });
    return () => controller.abort();
  }, []);
  return <span className="connection" role="status"><span className="status-dot" aria-hidden="true" />{status}</span>;
}

function AppContent() {
  const { phase, user, logout, message, retryVerification } = useAuth();
  return <div className="app-shell">
    <header className="site-header">
      <a className="brand" href="/" aria-label="Fair Share home"><span className="brand-mark" aria-hidden="true">f.</span>fair share<span className="brand-period">.</span></a>
      <div className="header-actions"><BackendStatus />{user && <button className="secondary" onClick={logout}>Sign out</button>}</div>
    </header>
    <main>
      {phase === 'loading' ? <section className="card centered"><h1>Getting things ready.</h1><p role="status">Checking your account…</p></section>
      : phase === 'error' ? <section className="card centered"><h1>Let’s reconnect.</h1><p role="alert">{message}</p><button className="primary" onClick={retryVerification}>Try again</button><button className="text-button" onClick={logout}>Return to sign in</button></section>
      : user ? <HouseholdProvider key={user.uid}><HouseholdWorkspace /></HouseholdProvider>
      : <div className="auth-layout"><section className="welcome-copy">
          <p className="eyebrow">A LITTLE MORE HARMONY AT HOME</p><h1>Roommate Expenses</h1>
          <p className="hero-line">One home.<br />A fair share.</p>
          <p className="hero-description">Make room for the good stuff. Keep shared bills, housemates, and the everyday in one place.</p>
          <div className="home-art" aria-hidden="true"><div className="roof" /><div className="house"><span /><span /><span /><span /></div><div className="art-caption">Better, together.</div></div>
          <p className="small-print">Built for the people you share a home with.</p>
        </section><AccountForm /></div>}
    </main>
    <footer className="site-footer"><span>Fair Share · Group 17</span><span>A shared home. A simpler everyday.</span></footer>
  </div>;
}

export default function App({ authClient }) {
  return <AuthProvider client={authClient}><AppContent /></AuthProvider>;
}
