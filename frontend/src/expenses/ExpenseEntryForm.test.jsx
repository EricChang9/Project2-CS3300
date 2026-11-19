import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App.jsx';
import { fakeAuth, mockApi, response, sdkUser } from '../test/auth.js';

const members = [
  { uid: 'alice', username: 'Alice' },
  { uid: 'bob', username: 'Bob' },
];
const home = { id: 'maple', name: 'Maple House', createdBy: 'alice', members };
const expensePath = '/api/households/maple/expenses';

function createdExpense(body, sharesCents) {
  return {
    id: 'expense-1', householdId: 'maple', ...body, sharesCents,
    createdBy: 'alice', createdAt: '2026-11-18T12:00:00Z',
  };
}

function renderWorkspace(createExpense = () => response({}, 201)) {
  const fetchMock = mockApi((path, options = {}) => {
    if (path === '/api/households' && options.method !== 'POST') return response([home]);
    if (path === expensePath && options.method === 'POST') return createExpense(options);
    return undefined;
  });
  render(<App authClient={fakeAuth(sdkUser('alice'))} />);
  return fetchMock;
}

async function readyForm() {
  await screen.findByRole('heading', { name: 'Add an expense' });
}

describe('household expense entry', () => {
  it('submits equal splits in integer cents and displays the server allocations', async () => {
    let submitted;
    const fetchMock = renderWorkspace(options => {
      submitted = JSON.parse(options.body);
      return response(createdExpense(submitted, { alice: 501, bob: 500 }), 201);
    });
    await readyForm();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Electricity' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '10.01' } });
    fireEvent.change(screen.getByLabelText('Paid by'), { target: { value: 'bob' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));

    expect(await screen.findByRole('status', { name: 'Expense saved' })).toHaveTextContent('Alice: $5.01');
    expect(screen.getByRole('status', { name: 'Expense saved' })).toHaveTextContent('Bob: $5.00');
    expect(submitted).toMatchObject({
      description: 'Electricity', amountCents: 1001, currency: 'USD', payerId: 'bob',
      participantIds: ['alice', 'bob'], category: 'OTHER', splitType: 'EQUAL',
    });
    expect(submitted.expenseDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(submitted).not.toHaveProperty('customAllocations');
    const call = fetchMock.mock.calls.find(([path, options]) => path === expensePath && options.method === 'POST');
    expect(call[1].headers.get('Authorization')).toBe('Bearer test-token');
    expect(screen.getByLabelText('Description')).toHaveValue('');
    expect(screen.getByLabelText('Amount (USD)')).toHaveValue('');
  });

  it('submits custom allocations as integer cents only when they match the total', async () => {
    let submitted;
    renderWorkspace(options => {
      submitted = JSON.parse(options.body);
      return response(createdExpense(submitted, submitted.customAllocations), 201);
    });
    await readyForm();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Groceries' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '10.01' } });
    fireEvent.click(screen.getByLabelText('Set custom shares'));
    fireEvent.change(screen.getByLabelText('Share for Alice (USD)'), { target: { value: '3.00' } });
    fireEvent.change(screen.getByLabelText('Share for Bob (USD)'), { target: { value: '7.01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));

    expect(await screen.findByRole('status', { name: 'Expense saved' })).toHaveTextContent('Alice: $3.00');
    expect(submitted).toMatchObject({
      amountCents: 1001,
      splitType: 'CUSTOM',
      customAllocations: { alice: 300, bob: 701 },
    });
  });

  it('rejects custom totals that do not equal the expense before sending', async () => {
    const fetchMock = renderWorkspace();
    await readyForm();
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Groceries' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '10.00' } });
    fireEvent.click(screen.getByLabelText('Set custom shares'));
    fireEvent.change(screen.getByLabelText('Share for Alice (USD)'), { target: { value: '3.00' } });
    fireEvent.change(screen.getByLabelText('Share for Bob (USD)'), { target: { value: '5.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Custom shares must add up to $10.00.');
    expect(fetchMock.mock.calls.some(([path, options]) => path === expensePath && options.method === 'POST')).toBe(false);
  });

  it('keeps the form pending until save completes, then shows a recoverable server error', async () => {
    let finish;
    const fetchMock = renderWorkspace(() => new Promise(resolve => { finish = resolve; }));
    await readyForm();
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Internet' } });
    fireEvent.change(screen.getByLabelText('Amount (USD)'), { target: { value: '45.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await screen.findByRole('button', { name: 'Saving expense…' })).toBeDisabled();
    expect(screen.getByLabelText('Description')).toBeDisabled();
    await waitFor(() => expect(finish).toBeTypeOf('function'));

    await act(async () => finish(response({}, 503)));
    expect(await screen.findByRole('alert')).toHaveTextContent('Expense saving is temporarily unavailable.');
    expect(screen.getByLabelText('Description')).toHaveValue('Internet');
    expect(screen.getByLabelText('Amount (USD)')).toHaveValue('45.00');
    expect(screen.getByRole('button', { name: 'Save expense' })).toBeEnabled();
    expect(fetchMock.mock.calls.filter(([path, options]) => path === expensePath && options.method === 'POST')).toHaveLength(1);
  });
});
