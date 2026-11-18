import { getApp, getApps, initializeApp } from 'firebase/app';
import { createUserWithEmailAndPassword, getAuth, onIdTokenChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth';

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};
const configured = Object.values(config).every(Boolean);
let auth;
function instance() {
  if (!configured) throw new Error('Account access is unavailable.');
  auth ??= getAuth(getApps().length ? getApp() : initializeApp(config));
  return auth;
}

export const firebaseAuthClient = {
  configured,
  subscribe(callback) {
    if (configured) return onIdTokenChanged(instance(), callback);
    let active = true;
    queueMicrotask(() => { if (active) callback(null); });
    return () => { active = false; };
  },
  signIn: (email, password) => signInWithEmailAndPassword(instance(), email, password),
  register: (email, password) => createUserWithEmailAndPassword(instance(), email, password),
  signOut: () => signOut(instance()),
};

export function authErrorMessage(error) {
  switch (error?.code) {
    case 'auth/email-already-in-use': return 'An account already uses that email. Sign in instead.';
    case 'auth/invalid-email': return 'Enter a valid email address.';
    case 'auth/weak-password': case 'auth/password-does-not-meet-requirements':
      return 'Choose a stronger password that meets your household app’s password policy.';
    case 'auth/too-many-requests': return 'Too many attempts. Please wait a little and try again.';
    case 'auth/network-request-failed': return 'Unable to connect. Check your connection and try again.';
    default: return 'Unable to sign in. Check your details and try again.';
  }
}
