import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';
import { AuthProvider, useAuth } from './AuthProvider.jsx';

function credentials() {
  fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'alice@example.com' } });
  fireEvent.change(screen.getByLabelText('Password', { exact: true }), { target: { value: 'correct-password' } });
}

describe('account screens', () => {
  it('registers with confirmation and verifies the signed-in identity', async () => {
    mockApi();
    const client = fakeAuth();
    client.register.mockImplementation(() => client.emit(sdkUser()));
    render(<App authClient={client} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create an account' }));
    credentials();
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'correct-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account', exact: true }));
    expect(await screen.findByRole('heading', { name: 'Welcome, Alice.' })).toBeInTheDocument();
    expect(client.register).toHaveBeenCalledWith('alice@example.com', 'correct-password');
  });

  it('rejects a mismatched confirmation without creating an account', async () => {
    mockApi(); const client = fakeAuth(); render(<App authClient={client} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create an account' }));
    credentials();
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'different' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account', exact: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The passwords do not match.');
    expect(client.register).not.toHaveBeenCalled();
  });

  it('shows safe sign-in errors and clears the password', async () => {
    mockApi(); const client = fakeAuth();
    client.signIn.mockRejectedValue({ code: 'auth/invalid-credential', message: 'secret-provider-detail' });
    render(<App authClient={client} />);
    await screen.findByRole('button', { name: 'Sign in', exact: true }); credentials();
    fireEvent.click(screen.getByRole('button', { name: 'Sign in', exact: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to sign in.');
    expect(screen.queryByText('secret-provider-detail')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Password', { exact: true })).toHaveValue('');
  });

  it('prevents repeated submits while signing in', async () => {
    mockApi(); const client = fakeAuth(); let finish;
    client.signIn.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    render(<App authClient={client} />);
    await screen.findByRole('button', { name: 'Sign in', exact: true }); credentials();
    fireEvent.click(screen.getByRole('button', { name: 'Sign in', exact: true }));
    expect(screen.getByRole('button', { name: 'Please wait…' })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText('Email address').closest('form'));
    expect(client.signIn).toHaveBeenCalledTimes(1);
    await act(async () => finish());
  });

  it('restores a verified session and clears it on sign-out', async () => {
    const fetchMock = mockApi(); const client = fakeAuth(sdkUser());
    render(<App authClient={client} />);
    expect(await screen.findByRole('heading', { name: 'Welcome, Alice.' })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([path]) => path === '/api/me');
    expect(call[1].headers.get('Authorization')).toBe('Bearer test-token');
    expect(call[1].credentials).toBe('omit');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByRole('button', { name: 'Sign in', exact: true })).toBeInTheDocument();
    expect(screen.queryByText('Welcome, Alice.')).not.toBeInTheDocument();
    expect(client.signOut).toHaveBeenCalledTimes(1);
  });

  it('ignores late verification after the SDK signs out', async () => {
    let finish;
    mockApi(path => path === '/api/me' ? new Promise(resolve => { finish = resolve; }) : undefined);
    const client = fakeAuth(sdkUser()); render(<App authClient={client} />);
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    await act(() => client.emit(null));
    await act(async () => finish(response({ uid: 'alice', displayName: 'Alice' })));
    expect(await screen.findByRole('button', { name: 'Sign in', exact: true })).toBeInTheDocument();
    expect(screen.queryByText('Welcome, Alice.')).not.toBeInTheDocument();
  });

  it('explains when the backend cannot verify an otherwise restored login', async () => {
    mockApi(path => path === '/api/me' ? response({}, 503) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not verify your account.');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('refreshes once on API 401, then returns an expired session to sign-in', async () => {
    const fetchMock = mockApi(path => path === '/api/private' ? response({}, 401) : undefined);
    const identity = sdkUser(); const client = fakeAuth(identity);
    function Probe() {
      const { user, request, message } = useAuth();
      return user ? <button onClick={() => request('/api/private').catch(() => {})}>Load private data</button>
        : <p>{message || 'Signed out'}</p>;
    }
    render(<AuthProvider client={client}><Probe /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Load private data' }));
    expect(await screen.findByText('Your session has expired. Please sign in again.')).toBeInTheDocument();
    expect(identity.getIdToken).toHaveBeenCalledWith(true);
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/private')).toHaveLength(2);
    expect(client.signOut).toHaveBeenCalledTimes(1);
  });
});
