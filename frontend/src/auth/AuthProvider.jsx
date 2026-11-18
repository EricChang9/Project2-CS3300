import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { firebaseAuthClient } from './firebase.js';

const AuthContext = createContext(null);
export class ApiError extends Error {
  constructor(status) { super('The request could not be completed.'); this.status = status; }
}

async function send(path, token, options = {}) {
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);
  if (options.body) headers.set('Content-Type', 'application/json');
  return fetch(path, { ...options, headers, credentials: 'omit' });
}

export function AuthProvider({ children, client = firebaseAuthClient }) {
  const [session, setSession] = useState({ phase: 'loading', user: null, sdkUser: null, message: '' });
  const generation = useRef(0);
  const acceptedUid = useRef(null);
  const liveSession = useRef(session);
  liveSession.current = session;
  const [retry, setRetry] = useState(0);

  const clearSession = useCallback((message = '') => {
    generation.current += 1;
    acceptedUid.current = null;
    liveSession.current = { phase: 'signedOut', user: null, sdkUser: null, message };
    setSession(liveSession.current);
  }, []);

  useEffect(() => {
    let active = true;
    let controller;
    const unsubscribe = client.subscribe(async sdkUser => {
      if (!active) return;
      if (sdkUser && acceptedUid.current === sdkUser.uid) return;
      const version = ++generation.current;
      controller?.abort();
      controller = new AbortController();
      if (!sdkUser) { clearSession(liveSession.current.message); return; }
      acceptedUid.current = null;
      liveSession.current = { phase: 'loading', user: null, sdkUser: null, message: '' };
      setSession(liveSession.current);
      try {
        const response = await send('/api/me', await sdkUser.getIdToken(), { signal: controller.signal });
        if (!response.ok) throw new ApiError(response.status);
        const user = await response.json();
        if (user.uid !== sdkUser.uid) throw new ApiError(401);
        if (active && version === generation.current) {
          acceptedUid.current = user.uid;
          setSession({ phase: 'signedIn', user, sdkUser, message: '' });
        }
      } catch (error) {
        if (!active || version !== generation.current || error.name === 'AbortError') return;
        if (error.status === 401 || error.code?.startsWith('auth/')) {
          clearSession('Your session has expired. Please sign in again.');
          await client.signOut().catch(() => {});
        } else {
          setSession({ phase: 'error', user: null, sdkUser: null,
            message: 'We could not verify your account. Check your connection and try again.' });
        }
      }
    });
    return () => { active = false; generation.current += 1; controller?.abort(); unsubscribe(); };
  }, [client, clearSession, retry]);

  const request = useCallback(async (path, options = {}) => {
    if (!path.startsWith('/api/')) throw new Error('Only same-origin application API paths are allowed.');
    const current = liveSession.current;
    const version = generation.current;
    if (!current.sdkUser) throw new ApiError(401);
    const expire = async () => {
      if (version === generation.current) {
        clearSession('Your session has expired. Please sign in again.');
        await client.signOut().catch(() => {});
      }
      throw new ApiError(401);
    };
    let token;
    try { token = await current.sdkUser.getIdToken(); }
    catch (error) {
      if (error.code?.startsWith('auth/')) return expire();
      throw error;
    }
    if (version !== generation.current) throw new DOMException('Session changed', 'AbortError');
    let response = await send(path, token, options);
    if (response.status === 401) {
      try { token = await current.sdkUser.getIdToken(true); }
      catch { return expire(); }
      if (version !== generation.current) throw new DOMException('Session changed', 'AbortError');
      response = await send(path, token, options);
      if (response.status === 401) return expire();
    }
    if (version !== generation.current) throw new DOMException('Session changed', 'AbortError');
    if (!response.ok) throw new ApiError(response.status);
    const data = response.status === 204 ? null : await response.json();
    if (version !== generation.current) throw new DOMException('Session changed', 'AbortError');
    return data;
  }, [client, clearSession]);

  async function logout() {
    clearSession();
    try { await client.signOut(); }
    catch { setSession(previous => ({ ...previous, message: 'Sign-out could not finish. Please try again.' })); }
  }

  return <AuthContext.Provider value={{ ...session, configured: client.configured, request,
    signIn: client.signIn, register: client.register, logout,
    retryVerification: () => { acceptedUid.current = null; setRetry(value => value + 1); } }}>
    {children}
  </AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth requires AuthProvider');
  return value;
}
