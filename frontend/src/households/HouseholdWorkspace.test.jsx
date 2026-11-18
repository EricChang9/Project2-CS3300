import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const home = (id, name, uid = 'alice') => ({ id, name, createdBy: uid, createdAt: '2026-11-17T20:00:00Z', members: [{ uid, joinedAt: '2026-11-17T20:00:00Z' }] });

describe('household workspace', () => {
  it('shows a useful empty state without invented households', async () => {
    mockApi(); render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByRole('heading', { name: 'Your home starts here.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create household', exact: true })).toBeInTheDocument();
    expect(screen.queryByLabelText('Current household')).not.toBeInTheDocument();
  });

  it('creates, refreshes, and selects a household using the authorized API', async () => {
    let created = false;
    const fetchMock = mockApi((path, options) => {
      if (path !== '/api/households') return;
      if (options.method === 'POST') { created = true; return response(home('maple', 'Maple House'), 201); }
      return response(created ? [home('maple', 'Maple House')] : []);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.change(await screen.findByLabelText('Household name'), { target: { value: '  Maple House  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create household', exact: true }));
    expect(await screen.findByText('Maple House was created.')).toBeInTheDocument();
    expect(screen.getByLabelText('Current household')).toHaveValue('maple');
    expect(screen.getByRole('heading', { name: 'Maple House' })).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(([path, options]) => path === '/api/households' && options.method === 'POST');
    expect(JSON.parse(post[1].body)).toEqual({ name: 'Maple House' });
    expect(post[1].headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('switches household members immediately without retaining the previous home', async () => {
    mockApi(path => path === '/api/households' ? response([home('a', 'Maple', 'alice'), home('b', 'Cedar', 'bob')]) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    const selection = await screen.findByLabelText('Current household');
    expect(screen.getByRole('heading', { name: 'Maple' })).toBeInTheDocument();
    fireEvent.change(selection, { target: { value: 'b' } });
    expect(screen.getByRole('heading', { name: 'Cedar' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Maple' })).not.toBeInTheDocument();
    expect(screen.queryByText('You', { exact: true })).not.toBeInTheDocument();
    expect(screen.getByText('bob', { exact: true })).toBeInTheDocument();
  });

  it('shows usernames instead of generic housemate labels or internal IDs', async () => {
    const household = home('named', 'Maple');
    household.members = [{ uid: 'alice', username: 'alice123' }, { uid: 'opaque-firebase-id', username: 'Joseph' }];
    mockApi(path => path === '/api/households' ? response([household]) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByText('Joseph', { exact: true })).toBeInTheDocument();
    expect(screen.getByText('alice123', { exact: true })).toBeInTheDocument();
    expect(screen.getByText('You', { exact: true })).toBeInTheDocument();
    expect(screen.queryByText('Housemate', { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText('opaque-firebase-id', { exact: true })).not.toBeInTheDocument();
  });

  it('does not override a newer selection when a refresh finishes', async () => {
    let finish; let calls = 0;
    mockApi(path => {
      if (path !== '/api/households') return;
      calls += 1;
      return calls === 1 ? response([home('a', 'Maple'), home('b', 'Cedar')]) : new Promise(resolve => { finish = resolve; });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    const selection = await screen.findByLabelText('Current household');
    fireEvent.click(screen.getByRole('button', { name: 'Refresh households' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    fireEvent.change(selection, { target: { value: 'b' } });
    await act(async () => finish(response([home('a', 'Maple'), home('b', 'Cedar')])));
    expect(selection).toHaveValue('b');
    expect(screen.getByRole('heading', { name: 'Cedar' })).toBeInTheDocument();
  });

  it('renders a recoverable list failure', async () => {
    let failed = true;
    mockApi(path => path === '/api/households' ? response(failed ? {} : [], failed ? 503 : 200) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your households.');
    failed = false; fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'Your home starts here.' })).toBeInTheDocument();
  });

  it('does not suggest another create when only the post-create refresh fails', async () => {
    let created = false;
    mockApi((path, options) => {
      if (path !== '/api/households') return;
      if (options.method === 'POST') { created = true; return response(home('a', 'Maple'), 201); }
      return created ? response({}, 503) : response([]);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.change(await screen.findByLabelText('Household name'), { target: { value: 'Maple' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create household', exact: true }));
    expect(await screen.findByText('Maple was created.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Maple' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('We could not load your households.');
  });
});
