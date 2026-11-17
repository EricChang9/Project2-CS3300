import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const home = (id, name) => ({ id, name, createdBy: 'alice', members: [{ uid: 'alice' }] });
const invitation = { code: 'case-Sensitive-7Z', expiresAt: '2030-01-01T00:00:00Z' };

describe('household invitations', () => {
  it('requests a code for the selected household and copies it', async () => {
    const writeText = vi.fn().mockResolvedValue();
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const fetchMock = mockApi(path => {
      if (path === '/api/households') return response([home('maple', 'Maple')]);
      if (path === '/api/households/maple/invitations') return response(invitation, 201);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create invitation code' }));
    expect(await screen.findByLabelText('Invitation code')).toHaveValue(invitation.code);
    fireEvent.click(screen.getByRole('button', { name: 'Copy code' }));
    expect(await screen.findByText('Invitation code copied.')).toBeInTheDocument();
    expect(writeText).toHaveBeenCalledWith(invitation.code);
    const call = fetchMock.mock.calls.find(([path]) => path === '/api/households/maple/invitations');
    expect(call[1].method).toBe('POST');
    expect(call[1].headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('joins from an empty account, refreshes membership, and selects the joined household', async () => {
    let joined = false;
    const fetchMock = mockApi(path => {
      if (path === '/api/households') return response(joined ? [home('cedar', 'Cedar')] : []);
      if (path === '/api/invitations/redeem') { joined = true; return response({ householdId: 'cedar', status: 'joined' }); }
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.change(await screen.findByLabelText('Your invitation code'), { target: { value: '  case-Sensitive-7Z  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join household' }));
    expect(await screen.findByText('You joined Cedar. Welcome home!')).toBeInTheDocument();
    expect(screen.getByLabelText('Current household')).toHaveValue('cedar');
    const call = fetchMock.mock.calls.find(([path]) => path === '/api/invitations/redeem');
    expect(JSON.parse(call[1].body)).toEqual({ code: invitation.code });
  });

  it.each([[400, /is invalid/], [404, /is invalid/], [410, /has expired/]])('explains a rejected code (%s)', async (status, message) => {
    mockApi(path => path === '/api/invitations/redeem' ? response({}, status) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.change(await screen.findByLabelText('Your invitation code'), { target: { value: 'bad-code' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join household' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('Your invitation code')).toHaveValue('bad-code');
  });

  it('handles an already-member result without claiming a second membership', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([home('cedar', 'Cedar')]);
      if (path === '/api/invitations/redeem') return response({ householdId: 'cedar', status: 'already_member' });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.change(await screen.findByLabelText('Your invitation code'), { target: { value: 'existing' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join household' }));
    expect(await screen.findByText('You’re already a member of Cedar.')).toBeInTheDocument();
    expect(screen.getByLabelText('Current household').options).toHaveLength(1);
  });

  it('prevents another redemption while a join is pending', async () => {
    let finish; let joined = false;
    const fetchMock = mockApi(path => {
      if (path === '/api/households') return response(joined ? [home('cedar', 'Cedar')] : []);
      if (path === '/api/invitations/redeem') return new Promise(resolve => { finish = resolve; });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    const input = await screen.findByLabelText('Your invitation code');
    fireEvent.change(input, { target: { value: 'join-code' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join household' }));
    expect(screen.getByRole('button', { name: 'Joining…' })).toBeDisabled();
    fireEvent.submit(input.closest('form'));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/invitations/redeem')).toHaveLength(1);
    joined = true;
    await act(async () => finish(response({ householdId: 'cedar', status: 'joined' })));
    expect(await screen.findByText('You joined Cedar. Welcome home!')).toBeInTheDocument();
  });

  it('ignores a late invitation after switching households', async () => {
    let finish;
    mockApi(path => {
      if (path === '/api/households') return response([home('a', 'Maple'), home('b', 'Cedar')]);
      if (path === '/api/households/a/invitations') return new Promise(resolve => { finish = resolve; });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create invitation code' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    fireEvent.change(screen.getByLabelText('Current household'), { target: { value: 'b' } });
    await act(async () => finish(response(invitation, 201)));
    expect(screen.queryByLabelText('Invitation code')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create invitation code' })).toBeEnabled();
  });

  it('offers manual copying when clipboard access fails', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } });
    mockApi(path => {
      if (path === '/api/households') return response([home('a', 'Maple')]);
      if (path === '/api/households/a/invitations') return response(invitation, 201);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create invitation code' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Copy code' }));
    expect(await screen.findByText('Select the invitation code and copy it manually.')).toBeInTheDocument();
    expect(screen.queryByText('Invitation code copied.')).not.toBeInTheDocument();
  });

  it('reports unavailable invitation APIs without fabricating a code', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([home('a', 'Maple')]);
      if (path === '/api/households/a/invitations') return response({}, 503);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Create invitation code' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invitations are unavailable right now.');
    expect(screen.queryByLabelText('Invitation code')).not.toBeInTheDocument();
  });
});
