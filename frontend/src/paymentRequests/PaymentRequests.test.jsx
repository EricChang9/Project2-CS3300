import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';
import { todayLocal } from './api.js';

const maple = {
  id: 'maple', name: 'Maple House', createdBy: 'alice', createdAt: '2026-11-17T20:00:00Z',
  members: [{ uid: 'alice', username: 'Alice' }, { uid: 'bob', username: 'Bob' },
    { uid: 'carol', username: 'Carol' }],
};
const cedar = { ...maple, id: 'cedar', name: 'Cedar House' };
const dinner = {
  id: 'dinner-1', householdId: 'maple', description: 'Shared dinner', amountCents: 1001,
  currency: 'USD', payerId: 'alice', participantIds: ['alice', 'bob', 'carol'],
  sharesCents: { alice: 501, bob: 500, carol: 0 }, expenseDate: '2026-11-18',
  category: 'GROCERIES', splitType: 'EQUAL',
};
const requestPath = '/api/households/maple/payment-requests';
const future = new Date();
future.setDate(future.getDate() + 30);
const dueDate = todayLocal(future);
const sentRequest = {
  id: 'r1', expenseId: 'dinner-1', expenseDescription: 'Shared dinner', householdId: 'maple',
  creditorId: 'alice', debtorId: 'bob', amountCents: 500, currency: 'USD',
  dueDate, status: 'OUTSTANDING',
};
const receivedRequest = {
  ...sentRequest, id: 'r2', expenseId: 'utilities-1', expenseDescription: 'Electric bill',
  creditorId: 'bob', debtorId: 'alice', amountCents: 2000, dueDate: null, status: 'PAID',
};

function setup(handler = () => undefined) {
  return mockApi((path, options) => {
    if (path === '/api/households') return response([maple]);
    if (path === '/api/households/maple/expenses?limit=20') return response({ items: [dinner], nextCursor: null });
    if (path === '/api/households/maple/expenses/dinner-1') return response(dinner);
    return handler(path, options);
  });
}

async function openExpense() {
  fireEvent.click(await screen.findByRole('button', { name: 'View Shared dinner' }));
  await screen.findByRole('heading', { name: 'Participant shares' });
}

function panel() { return screen.getByRole('region', { name: 'Payment requests' }); }

async function openRequestForm() {
  await openExpense();
  fireEvent.click(await screen.findByRole('button', { name: 'Request $5.00 from Bob' }));
}

describe('payment request frontend', () => {
  it('creates a request with a due date and refreshes the sent list', async () => {
    let items = [];
    const fetchMock = setup((path, options) => {
      if (path === requestPath && options?.method === 'POST') {
        items = [sentRequest];
        return response(sentRequest, 201);
      }
      if (path === requestPath) return response({ items });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    await openExpense();
    expect(screen.queryByRole('button', { name: /Request .* from Carol/ })).not.toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: 'Request $5.00 from Bob' }));
    fireEvent.change(screen.getByLabelText('Due date (optional)'), { target: { value: dueDate } });
    fireEvent.click(screen.getByRole('button', { name: 'Send request' }));

    expect(await screen.findByText('Payment request sent.')).toBeInTheDocument();
    expect(await within(panel()).findByText('Shared dinner')).toBeInTheDocument();
    expect(within(panel()).getByRole('region', { name: 'Sent requests' })).toHaveTextContent('Outstanding');
    expect(screen.queryByRole('button', { name: 'Request $5.00 from Bob' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([path, options]) => path === requestPath
      && options?.method === 'POST'
      && JSON.parse(options.body).expenseId === 'dinner-1'
      && JSON.parse(options.body).debtorId === 'bob'
      && JSON.parse(options.body).dueDate === dueDate)).toBe(true);
  });

  it('sends a request without a due date when the field is blank', async () => {
    const fetchMock = setup((path, options) => path === requestPath && options?.method === 'POST'
      ? response(sentRequest, 201) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    await openRequestForm();
    fireEvent.click(screen.getByRole('button', { name: 'Send request' }));
    await screen.findByText('Payment request sent.');
    const [, options] = fetchMock.mock.calls.find(([path, next]) => path === requestPath && next?.method === 'POST');
    expect(JSON.parse(options.body)).toEqual({ expenseId: 'dinner-1', debtorId: 'bob' });
  });

  it('shows sent and received requests with paid status and detail fields', async () => {
    setup(path => {
      if (path === requestPath) return response({ items: [sentRequest, receivedRequest] });
      if (path === `${requestPath}/r2`) return response(receivedRequest);
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    const view = await screen.findByRole('region', { name: 'Payment requests' });
    expect(await within(view).findByText('Electric bill')).toBeInTheDocument();
    const sent = within(view).getByRole('region', { name: 'Sent requests' });
    const received = within(view).getByRole('region', { name: 'Received requests' });
    expect(sent).toHaveTextContent('Shared dinner');
    expect(sent).toHaveTextContent('Outstanding');
    expect(received).toHaveTextContent('Electric bill');
    expect(received).toHaveTextContent('Paid');
    fireEvent.click(within(received).getByRole('button', { name: 'View request r2' }));
    await within(view).findByRole('heading', { name: 'Electric bill' });
    expect(view).toHaveTextContent('Creditor');
    expect(view).toHaveTextContent('Bob');
    expect(view).toHaveTextContent('Debtor');
    expect(view).toHaveTextContent('Alice');
    expect(view).toHaveTextContent('$20.00');
    expect(view).toHaveTextContent('No due date');
    expect(view).toHaveTextContent('Paid');
    fireEvent.click(within(view).getByRole('button', { name: '← Back to requests' }));
    expect(within(view).getByRole('region', { name: 'Received requests' })).toBeInTheDocument();
  });

  it('rejects past dates before sending to the backend', async () => {
    const fetchMock = setup();
    render(<App authClient={fakeAuth(sdkUser())} />);
    await openRequestForm();
    fireEvent.change(screen.getByLabelText('Due date (optional)'), { target: { value: '2020-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send request' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a valid due date');
    expect(fetchMock.mock.calls.some(([path, options]) => path === requestPath && options?.method === 'POST')).toBe(false);
  });

  it.each([
    [409, 'An active request already exists for this share.'],
    [403, 'You are not allowed to manage this payment request.'],
    [400, 'This share or due date is no longer valid.'],
  ])('shows backend failure %s without claiming success', async (status, message) => {
    setup((path, options) => path === requestPath && options?.method === 'POST'
      ? response({}, status) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    await openRequestForm();
    fireEvent.click(screen.getByRole('button', { name: 'Send request' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByText('Payment request sent.')).not.toBeInTheDocument();
  });

  it('does not offer request actions to the debtor', async () => {
    setup(path => path === '/api/me'
      ? response({ uid: 'bob', displayName: 'Bob', email: 'bob@example.com' }) : undefined);
    render(<App authClient={fakeAuth(sdkUser('bob'))} />);
    await openExpense();
    expect(screen.queryByRole('button', { name: 'Request $5.00 from Bob' })).not.toBeInTheDocument();
  });

  it('hides request actions for an already requested share', async () => {
    setup(path => path === requestPath ? response({ items: [receivedRequest, { ...sentRequest, status: 'PAID' }] }) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    await within(await screen.findByRole('region', { name: 'Payment requests' })).findByText('Electric bill');
    await openExpense();
    expect(screen.queryByRole('button', { name: 'Request $5.00 from Bob' })).not.toBeInTheDocument();
  });

  it('shows a request list error and allows retry', async () => {
    let failed = true;
    setup(path => path === requestPath ? failed ? response({}, 503) : response({ items: [sentRequest] }) : undefined);
    render(<App authClient={fakeAuth(sdkUser())} />);
    const view = await screen.findByRole('region', { name: 'Payment requests' });
    expect(await within(view).findByRole('alert')).toHaveTextContent('We could not load payment requests.');
    failed = false;
    fireEvent.click(within(view).getByRole('button', { name: 'Try again' }));
    expect(await within(view).findByText('Shared dinner')).toBeInTheDocument();
  });

  it('keeps request lists separate when switching households', async () => {
    mockApi(path => {
      if (path === '/api/households') return response([maple, cedar]);
      if (path === requestPath) return response({ items: [sentRequest] });
      if (path === '/api/households/cedar/payment-requests') return response({ items: [] });
    });
    render(<App authClient={fakeAuth(sdkUser())} />);
    expect(await within(await screen.findByRole('region', { name: 'Payment requests' })).findByText('Shared dinner')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Current household'), { target: { value: 'cedar' } });
    const view = await screen.findByRole('region', { name: 'Payment requests' });
    await within(view).findByText('No sent requests yet.');
    await waitFor(() => expect(within(view).queryByText('Shared dinner')).not.toBeInTheDocument());
  });
});
