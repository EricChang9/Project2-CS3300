import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const home = id => ({ id, name: id === 'maple' ? 'Maple House' : 'Cedar House',
  createdBy: 'alice', members: [{ uid: 'alice', username: 'Alice' }] });
const month = () => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
};
const path = (id, value) => `/api/households/${id}/expenses/summary?month=${value}`;
const spending = total => ({ month: month(), currencies: [{ currency: 'USD', totalCents: total,
  expenseCount: 2, categories: [{ category: 'GROCERIES', totalCents: total - 400 },
    { category: 'UTILITIES', totalCents: 400 }] }] });
const panel = () => screen.getByRole('region', { name: 'Monthly spending' });

describe('monthly spending', () => {
  it('shows household totals and a category breakdown', async () => {
    mockApi(requestPath => requestPath === '/api/households' ? response([home('maple')])
      : requestPath === path('maple', month()) ? response(spending(1500)) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);

    const view = await screen.findByRole('region', { name: 'Monthly spending' });
    expect(await within(view).findByText('$15.00')).toBeInTheDocument();
    expect(within(view).getByText('2 expenses')).toBeInTheDocument();
    expect(within(view).getByText('Groceries')).toBeInTheDocument();
    expect(within(view).getByText('Utilities')).toBeInTheDocument();
    expect(within(view).getByText('$11.00')).toBeInTheDocument();
    expect(within(view).getByText('$4.00')).toBeInTheDocument();
  });

  it('changes the month and shows an empty state', async () => {
    const fetchMock = mockApi(requestPath => requestPath === '/api/households' ? response([home('maple')])
      : requestPath === path('maple', '2026-10') ? response({ month: '2026-10', currencies: [] }) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    await screen.findByRole('region', { name: 'Monthly spending' });
    fireEvent.change(within(panel()).getByLabelText('Month'), { target: { value: '2026-10' } });
    expect(await within(panel()).findByText('No expenses recorded for this month.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([requestPath]) => requestPath === path('maple', '2026-10'))).toBe(true);
  });

  it('refreshes after expense creation', async () => {
    let created = false;
    let reads = 0;
    mockApi((requestPath, options) => {
      if (requestPath === '/api/households') return response([home('maple')]);
      if (requestPath === '/api/households/maple/expenses' && options.method === 'POST') {
        created = true;
        return response({ id: 'new', description: 'Internet bill', amountCents: 300,
          sharesCents: { alice: 300 } }, 201);
      }
      if (requestPath === path('maple', month())) {
        reads += 1;
        return response(created ? spending(1500) : { month: month(), currencies: [] });
      }
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    const view = await screen.findByRole('region', { name: 'Monthly spending' });
    expect(await within(view).findByText('No expenses recorded for this month.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Internet bill' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '3.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await within(panel()).findByText('$15.00')).toBeInTheDocument();
    expect(reads).toBeGreaterThanOrEqual(2);
  });

  it('shows a retry action when the summary request fails', async () => {
    let failed = true;
    mockApi(requestPath => requestPath === '/api/households' ? response([home('maple')])
      : requestPath === path('maple', month()) ? failed ? response({}, 503) : response(spending(1500)) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    const view = await screen.findByRole('region', { name: 'Monthly spending' });
    expect(await within(view).findByRole('alert')).toHaveTextContent('We could not load monthly spending.');
    failed = false;
    fireEvent.click(within(panel()).getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(within(panel()).getByText('$15.00')).toBeInTheDocument());
  });
});
