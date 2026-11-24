import { test, expect } from '@playwright/test';
import fs from 'node:fs';
const identities = JSON.parse(fs.readFileSync(process.env.BB_IDENTITIES_FILE, 'utf8'));
const API = process.env.BB_API_URL;
let sequence = 0;

async function api(method, path, body, role = 'alice') {
  const response = await fetch(API + path, { method, headers: { Authorization: `Bearer ${identities[role].token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  expect(response.ok, `Fixture HTTP ${response.status}`).toBeTruthy(); return response.json();
}
async function home(two = true) {
  const created = await api('POST', '/api/households', { name: `Browser home ${process.env.BB_RUN_ID}-${++sequence}` });
  if (two) {
    const code = await api('POST', `/api/households/${created.id}/invitations`);
    await api('POST', '/api/invitations/redeem', { code: code.code }, 'bob');
  }
  return created;
}
async function login(page, role = 'alice') {
  await page.goto('/');
  await page.getByLabel('Email address').fill(identities[role].email);
  await page.getByLabel('Password', { exact: true }).fill('Blackbox-pass-42');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: `Welcome, ${role[0].toUpperCase()+role.slice(1)}.` })).toBeVisible();
}

test('BBS01 sign in, restore after reload, and sign out', async ({ page }) => {
  await login(page);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Welcome, Alice.' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Welcome, Alice.' })).toHaveCount(0);
});
test('BBS02 account registration rejects mismatched password confirmation', async ({ page }) => {
  await page.goto('/'); await page.getByRole('button', { name: 'Create an account' }).click();
  await page.getByLabel('Email address').fill(identities.charlie.email);
  await page.getByLabel('Password', { exact: true }).fill('Blackbox-pass-42');
  await page.getByLabel('Confirm password').fill('different-password');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('The passwords do not match.');
  await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
});
test('BBS03 sign-in failure has a useful non-secret error', async ({ page }) => {
  await page.goto('/'); await page.getByLabel('Email address').fill(identities.alice.email);
  await page.getByLabel('Password', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Unable to sign in.');
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('');
});
test('BBS04 creating a household displays its real name and member username', async ({ page }) => {
  await login(page);
  const name = `UI-created ${process.env.BB_RUN_ID}`;
  await page.getByLabel('Household name', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Create household', exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  const members = page.getByRole('list').filter({ has: page.getByText('Alice', { exact: true }) });
  await expect(members.getByText('Alice', { exact: true })).toBeVisible();
});
test('BBS05 expense form submits 1.01 USD with an exact 51/50-cent equal split', async ({ page }) => {
  const created = await home(); await login(page);
  await page.getByLabel('Current household').selectOption(created.id);
  await page.getByLabel('Description', { exact: true }).fill('Browser rounding receipt');
  await page.getByLabel('Amount (USD)', { exact: true }).fill('1.01');
  await page.getByLabel('Paid by', { exact: true }).selectOption(identities.alice.uid);
  await page.getByLabel('Expense date', { exact: true }).fill('2026-11-20');
  // Participants are selected through their visible checkbox controls, not app state.
  const participants = page.getByRole('checkbox');
  for (let i = 0; i < await participants.count(); i++) await participants.nth(i).check();
  await page.getByRole('button', { name: 'Save expense', exact: true }).click();
  await expect(page.getByRole('status', { name: 'Expense saved' })).toBeVisible();
  const history = await api('GET', `/api/households/${created.id}/expenses`);
  const expense = history.items.find(item => item.description === 'Browser rounding receipt');
  expect(expense.amountCents).toBe(101);
  expect(Object.values(expense.sharesCents).sort((a,b) => a-b)).toEqual([50,51]);
});
test('BBS06 changing household clears the previous expense history', async ({ page }) => {
  const first = await home(), second = await home();
  await api('POST', `/api/households/${first.id}/expenses`, { description:'Only in first home', amountCents:100,
    currency:'USD', payerId:identities.alice.uid, participantIds:[identities.alice.uid],
    expenseDate:'2026-11-20', category:'OTHER', splitType:'EQUAL' });
  await login(page); await page.getByLabel('Current household').selectOption(first.id);
  await expect(page.getByRole('button', { name: 'View Only in first home' })).toBeVisible();
  await page.getByLabel('Current household').selectOption(second.id);
  await expect(page.getByRole('heading', { name:second.name, exact:true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'View Only in first home' })).toHaveCount(0);
});
test('BBS07 invitation generation and join connect two browser users', async ({ page, browser }) => {
  const created = await home(false); await login(page);
  await page.getByLabel('Current household').selectOption(created.id);
  await page.getByRole('button', { name:'Create invitation code' }).click();
  const code = await page.getByLabel('Invitation code', { exact:true }).inputValue();
  expect(code).toMatch(/^[A-Za-z0-9_-]{43}$/);
  const context = await browser.newContext();
  try {
    const guest = await context.newPage(); await login(guest, 'bob');
    await guest.getByLabel('Your invitation code').fill(code);
    await guest.getByRole('button', { name:'Join household', exact:true }).click();
    await expect(guest.getByText(`You joined ${created.name}. Welcome home!`)).toBeVisible();
    await expect(guest.getByRole('heading', { name:created.name, exact:true })).toBeVisible();
  } finally { await context.close(); }
});
