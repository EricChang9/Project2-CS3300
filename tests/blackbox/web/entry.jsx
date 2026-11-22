import React from 'react';
import { createRoot } from 'react-dom/client';
import App from '../../../frontend/src/App.jsx';
import '../../../frontend/src/styles.css';

// Only the external identity provider is simulated. App, API calls, validation,
// household/expense services and database are the actual application.
const identities = await fetch('/__blackbox/identities').then(response => response.json());
const listeners = new Set();
const current = () => {
  const role = sessionStorage.getItem('blackbox-role');
  return role && identities[role] ? { uid: identities[role].uid, getIdToken: async () => identities[role].token } : null;
};
const emit = async () => Promise.all([...listeners].map(callback => callback(current())));
const client = {
  configured: true,
  subscribe(callback) { listeners.add(callback); queueMicrotask(() => { if (listeners.has(callback)) callback(current()); }); return () => listeners.delete(callback); },
  async signIn(email, password) {
    const role = Object.keys(identities).find(key => identities[key].email === email);
    if (!role || password !== 'Blackbox-pass-42') throw { code: 'auth/invalid-credential' };
    sessionStorage.setItem('blackbox-role', role); await emit();
  },
  async register(email, password) {
    if (email !== identities.charlie.email || password.length < 6) throw { code: 'auth/invalid-credential' };
    sessionStorage.setItem('blackbox-role', 'charlie'); await emit();
  },
  async signOut() { sessionStorage.removeItem('blackbox-role'); await emit(); },
};
createRoot(document.getElementById('root')).render(<React.StrictMode><App authClient={client} /></React.StrictMode>);
