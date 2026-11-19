import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const maple = {
  id: 'maple', name: 'Maple House', createdBy: 'alice', createdAt: '2026-11-17T20:00:00Z',
  members: [{ uid: 'alice', username: 'Alice' }, { uid: 'bob', username: 'Bob' }],
};
const cedar = {
  id: 'cedar', name: 'Cedar House', createdBy: 'alice', createdAt: '2026-11-17T20:00:00Z',
  members: [{ uid: 'alice', username: 'Alice' }],
};
const dinner = {
  id: 'dinner-1', householdId: 'maple', description: 'Shared dinner', amountCents: 1001,
  currency: 'USD', payerId: 'alice', participantIds: ['alice', 'bob'], expenseDate: '2026-11-16',
  category: 'GROCERIES', splitType: 'EQUAL', sharesCents: { alice: 501, bob: 500 },
};
const page = (items, nextCursor = null) => response({ items, nextCursor });

function expensePath(path) { return path.startsWith('/api/households/') && path.includes('/expenses'); }

describe('expense history and details', () => {
  it('lists household expenses and displays stored allocations in detail', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([maple]);
      if (path === '/api/households/maple/expenses?limit=20') return page([dinner]);
      if (path === '/api/households/maple/expenses/dinner-1') return response(dinner);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);

    const row = await screen.findByRole('button', { name: 'View Shared dinner' });
    expect(within(row).getByText('Nov 16, 2026')).toBeInTheDocument();
    expect(within(row).getByText('Groceries')).toBeInTheDocument();
    expect(within(row).getByText('Alice')).toBeInTheDocument();
    expect(within(row).getAllByText('$10.01')).toHaveLength(2);
    expect(within(row).getByText('Equal shares')).toBeInTheDocument();

    fireEvent.click(row);
    await screen.findByRole('heading', { name: 'Participant shares' });
    const allocations = screen.getByRole('region', { name: 'Participant shares' });
    expect(await within(allocations).findByText('Bob')).toBeInTheDocument();
    expect(within(allocations).getByText('$5.01')).toBeInTheDocument();
    expect(within(allocations).getByText('$5.00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '← Back to expenses' })).toBeInTheDocument();
  });

  it('shows an empty state and loads the next page with the cursor', async () => {
    const fetchMock = mockApi(path => {
      if (path === '/api/households') return response([maple]);
      if (path === '/api/households/maple/expenses?limit=20') return page([], 'after-page-one');
      if (path.includes('cursor=after-page-one')) return page([{ ...dinner, id: 'page-two', description: 'Second page' }]);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByText('No expenses have been recorded for this household yet.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Load more expenses' }));
    expect(await screen.findByRole('button', { name: 'View Second page' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([path]) => path.includes('cursor=after-page-one'))).toBe(true);
  });

  it('refreshes the first history page after a successful expense save', async () => {
    let created = false;
    let listRequests = 0;
    const fetchMock = mockApi((path, options) => {
      if (path === '/api/households') return response([maple]);
      if (path === '/api/households/maple/expenses' && options.method === 'POST') {
        created = true;
        return response({ ...dinner, description: 'New expense' }, 201);
      }
      if (path === '/api/households/maple/expenses?limit=20') {
        listRequests += 1;
        return page(created ? [{ ...dinner, description: 'New expense' }] : []);
      }
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    await screen.findByText('No expenses have been recorded for this household yet.');
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'New expense' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '10.01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await screen.findByRole('button', { name: 'View New expense' })).toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Expense saved' })).toBeInTheDocument();
    expect(listRequests).toBeGreaterThanOrEqual(2);
    expect(fetchMock.mock.calls.some(([path, options]) => path === '/api/households/maple/expenses' && options.method === 'POST')).toBe(true);
  });

  it('clears the previous household history when switching households', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([maple, cedar]);
      if (path === '/api/households/maple/expenses?limit=20') return page([dinner]);
      if (path === '/api/households/cedar/expenses?limit=20') return page([{ ...dinner, id: 'cedar-expense', householdId: 'cedar', description: 'Cedar groceries' }]);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByRole('button', { name: 'View Shared dinner' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Current household'), { target: { value: 'cedar' } });
    expect(await screen.findByRole('button', { name: 'View Cedar groceries' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'View Shared dinner' })).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'Cedar House' })).toBeInTheDocument();
  });

  it('shows a missing-resource state for a deleted or out-of-household expense', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([maple]);
      if (path === '/api/households/maple/expenses?limit=20') return page([dinner]);
      if (path === '/api/households/maple/expenses/dinner-1') return response({}, 404);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    fireEvent.click(await screen.findByRole('button', { name: 'View Shared dinner' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This expense was not found in this household.');
  });

  it('shows a recoverable history failure', async () => {
    let failed = true;
    mockApi(path => {
      if (path === '/api/households') return response([maple]);
      if (expensePath(path)) return failed ? response({}, 503) : page([]);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load household expenses.');
    failed = false;
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('No expenses have been recorded for this household yet.')).toBeInTheDocument();
  });
});
