import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const home = (id = 'maple', name = 'Maple House') => ({
  id, name, createdBy: 'alice', createdAt: '2026-11-17T20:00:00Z',
  members: [{ uid: 'alice', username: 'Alice' }, { uid: 'bob', username: 'Bob' }],
});

const balancePath = id => `/api/households/${id}/balances`;
const member = (memberId, owesCents, owedCents, netBalanceCents) => ({ memberId, byCurrency: {
  USD: { owesCents, owedCents, netBalanceCents },
} });
const balances = ({ aliceOwes = 0, aliceOwed = 0, aliceNet = aliceOwed - aliceOwes,
  bobOwes = aliceOwed, bobOwed = aliceOwes, bobNet = bobOwed - bobOwes, debts = [] } = {}) => ({
  householdId: 'maple', debts,
  members: [member('alice', aliceOwes, aliceOwed, aliceNet), member('bob', bobOwes, bobOwed, bobNet)],
  totals: [{ currency: 'USD', totalOwingCents: debts.reduce((sum, debt) => sum + debt.amountCents, 0),
    totalOwedCents: debts.reduce((sum, debt) => sum + debt.amountCents, 0) }],
});

function panel() { return screen.getByRole('region', { name: 'Household balances' }); }

describe('household balance view', () => {
  it('distinguishes amounts the current user owes from amounts owed to them', async () => {
    const result = balances({ aliceOwed: 450, aliceNet: 450,
      debts: [{ debtorId: 'bob', creditorId: 'alice', currency: 'USD', amountCents: 450 }] });
    mockApi(path => path === '/api/households' ? response([home()])
      : path === balancePath('maple') ? response(result) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    const view = await screen.findByRole('region', { name: 'Household balances' });
    await within(view).findByText('Owed to you');
    expect(within(view).getByText('You owe')).toBeInTheDocument();
    expect(within(view).getByText('Owed to you')).toBeInTheDocument();
    expect(within(view).getByRole('listitem')).toHaveTextContent('Bob owes Alice');
    expect(within(view).getByText('You’re owed $4.50')).toBeInTheDocument();
  });

  it('shows a personal debt and who should receive it', async () => {
    const result = balances({ aliceOwes: 725, aliceNet: -725,
      debts: [{ debtorId: 'alice', creditorId: 'bob', currency: 'USD', amountCents: 725 }] });
    mockApi(path => path === '/api/households' ? response([home()])
      : path === balancePath('maple') ? response(result) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    const view = await screen.findByRole('region', { name: 'Household balances' });
    await within(view).findByRole('listitem');
    expect(within(view).getByText('You owe $7.25')).toBeInTheDocument();
    expect(within(view).getByRole('listitem')).toHaveTextContent('Alice owes Bob');
  });

  it('clearly marks a settled household', async () => {
    mockApi(path => path === '/api/households' ? response([home()])
      : path === balancePath('maple') ? response(balances()) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    const view = await screen.findByRole('region', { name: 'Household balances' });
    await within(view).findByText('Everyone is settled up.');
    expect(within(view).getByText('You’re settled up')).toBeInTheDocument();
    expect(within(view).getByText('Everyone is settled up.')).toBeInTheDocument();
    expect(within(view).queryByText('Alice owes Bob')).not.toBeInTheDocument();
  });

  it('shows a loading state while the backend balance is pending', async () => {
    let resolveBalances;
    mockApi(path => path === '/api/households' ? response([home()])
      : path === balancePath('maple') ? new Promise(resolve => { resolveBalances = resolve; }) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    expect(await screen.findByText('Loading balances…')).toBeInTheDocument();
    await act(async () => resolveBalances(response(balances())));
    expect(await within(panel()).findByText('Everyone is settled up.')).toBeInTheDocument();
  });

  it('allows retry after a balance read failure', async () => {
    let failed = true;
    mockApi(path => path === '/api/households' ? response([home()])
      : path === balancePath('maple') ? failed ? response({}, 503) : response(balances()) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load household balances.');
    failed = false;
    fireEvent.click(within(panel()).getByRole('button', { name: 'Try again' }));
    expect(await within(panel()).findByText('Everyone is settled up.')).toBeInTheDocument();
  });

  it('refreshes after an expense is saved and clears data when switching households', async () => {
    let created = false;
    let balanceReads = 0;
    const fetchMock = mockApi((path, options) => {
      if (path === '/api/households') return response([home(), home('cedar', 'Cedar House')]);
      if (path === '/api/households/maple/expenses' && options.method === 'POST') {
        created = true;
        return response({ id: 'new-expense', description: 'Internet bill', amountCents: 300,
          sharesCents: { alice: 150, bob: 150 } }, 201);
      }
      if (path === balancePath('maple')) {
        balanceReads += 1;
        return response(created ? balances({ aliceOwed: 150, aliceNet: 150,
          debts: [{ debtorId: 'bob', creditorId: 'alice', currency: 'USD', amountCents: 150 }] }) : balances());
      }
      if (path === balancePath('cedar')) return response({
        householdId: 'cedar', debts: [], members: [member('alice', 0, 0, 0)],
        totals: [{ currency: 'USD', totalOwingCents: 0, totalOwedCents: 0 }],
      });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    await screen.findByText('Everyone is settled up.');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Internet bill' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '3.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await within(panel()).findByText('You’re owed $1.50')).toBeInTheDocument();
    expect(balanceReads).toBeGreaterThanOrEqual(2);
    expect(fetchMock.mock.calls.some(([path, requestOptions]) => path === '/api/households/maple/expenses'
      && requestOptions.method === 'POST')).toBe(true);

    fireEvent.change(screen.getByLabelText('Current household'), { target: { value: 'cedar' } });
    expect(await within(panel()).findByText('Everyone is settled up.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Cedar House' })).toBeInTheDocument();
    await waitFor(() => expect(within(panel()).queryByText('You’re owed $1.50')).not.toBeInTheDocument());
  });
});
