import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';
import { paymentRequestsPath, requestErrorMessage } from './api.js';

export default function usePaymentRequests(householdId) {
  const { request } = useAuth();
  const [version, setVersion] = useState(0);
  const [data, setData] = useState({ householdId: null, items: [], error: '', loading: false });
  const refresh = useCallback(() => setVersion(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setData({ householdId, items: [], error: '', loading: !!householdId });
    if (!householdId) return () => controller.abort();

    async function load() {
      try {
        const result = await request(paymentRequestsPath(householdId), { signal: controller.signal });
        if (!controller.signal.aborted) {
          const items = Array.isArray(result) ? result : result?.items;
          if (!Array.isArray(items)) throw new Error('Invalid payment request list');
          setData({ householdId, items, error: '', loading: false });
        }
      } catch (failure) {
        if (failure.name !== 'AbortError' && !controller.signal.aborted) {
          setData({ householdId, items: [], error: requestErrorMessage(failure), loading: false });
        }
      }
    }

    load();
    return () => controller.abort();
  }, [householdId, request, version]);

  return { ...(data.householdId === householdId ? data
    : { items: [], error: '', loading: !!householdId }), refresh };
}
