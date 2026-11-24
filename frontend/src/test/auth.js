import { vi } from 'vitest';

export function fakeAuth(initialUser = null) {
  let user = initialUser;
  const listeners = new Set();
  const client = {
    configured: true,
    subscribe(callback) {
      listeners.add(callback);
      queueMicrotask(() => { if (listeners.has(callback)) callback(user); });
      return () => listeners.delete(callback);
    },
    async emit(next) { user = next; await Promise.all([...listeners].map(callback => callback(next))); },
    signIn: vi.fn(), register: vi.fn(), signOut: vi.fn(),
  };
  client.signOut.mockImplementation(() => client.emit(null));
  return client;
}

export function sdkUser(uid = 'alice') {
  return { uid, getIdToken: vi.fn().mockResolvedValue('test-token') };
}

export function response(data, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => data };
}

export function mockApi(handler = () => undefined) {
  const fetchMock = vi.fn(async (path, options) => {
    const custom = await handler(path, options);
    if (custom !== undefined) return custom;
    if (path === '/api/health') return response({ status: 'UP' });
    if (path === '/api/me') return response({ uid: 'alice', email: 'alice@example.com', displayName: 'Alice' });
    if (path === '/api/households') return response([]);
    if (/^\/api\/households\/[^/]+\/expenses\/summary\?month=/.test(path)) {
      return response({ month: path.split('month=')[1], currencies: [] });
    }
    if (/^\/api\/households\/[^/]+\/expenses(?:\/[^/]+)?(?:\?.*)?$/.test(path)) {
      return path.includes('?') || /\/expenses\/[^/]+$/.test(path)
        ? response({ id: path.split('/').at(-1), description: 'Test expense', amountCents: 100, sharesCents: {} })
        : response({ items: [], nextCursor: null });
    }
    if (/^\/api\/households\/[^/]+\/payment-requests$/.test(path) && (!options?.method || options.method === 'GET')) {
      return response({ items: [] });
    }
    if (/^\/api\/households\/[^/]+\/balances$/.test(path)) {
      return response({ householdId: path.split('/')[3], debts: [],
        members: [{ memberId: 'alice', byCurrency: {} }], totals: [] });
    }
    throw new Error(`Unexpected test API: ${path}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
