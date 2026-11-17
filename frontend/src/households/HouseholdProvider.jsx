import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthProvider.jsx';

const HouseholdContext = createContext(null);

export function HouseholdProvider({ children }) {
  const { request } = useAuth();
  const [households, setHouseholds] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const selected = useRef('');
  const sequence = useRef(0);
  const selectionVersion = useRef(0);
  const controller = useRef(null);
  const active = useRef(false);

  const select = useCallback(id => {
    selectionVersion.current += 1;
    selected.current = id;
    setSelectedId(id);
  }, []);

  const refresh = useCallback(async ({ selectId } = {}) => {
    const version = ++sequence.current;
    const selectionAtStart = selectionVersion.current;
    controller.current?.abort();
    const abort = new AbortController();
    controller.current = abort;
    setLoading(true); setError('');
    try {
      const data = await request('/api/households', { signal: abort.signal });
      if (!active.current || version !== sequence.current) return;
      setHouseholds(data);
      const wanted = selectId && selectionAtStart === selectionVersion.current ? selectId : selected.current;
      select(data.some(home => home.id === wanted) ? wanted : data[0]?.id || '');
      return data;
    } catch (failure) {
      if (active.current && version === sequence.current && failure.name !== 'AbortError') {
        setError('We could not load your households. Please try again.');
      }
      return null;
    } finally {
      if (active.current && version === sequence.current) setLoading(false);
    }
  }, [request, select]);

  useEffect(() => {
    active.current = true;
    refresh();
    return () => { active.current = false; sequence.current += 1; controller.current?.abort(); };
  }, [refresh]);

  async function create(name) {
    const home = await request('/api/households', { method: 'POST', body: JSON.stringify({ name }) });
    if (!active.current) return;
    setHouseholds(previous => [home, ...previous.filter(item => item.id !== home.id)]);
    select(home.id);
    // Creation already succeeded. A refresh failure must not invite duplicate creation.
    await refresh();
    return home;
  }

  return <HouseholdContext.Provider value={{ households, selectedId,
    selectedHousehold: households.find(home => home.id === selectedId) || null,
    loading, error, select, create, refresh }}>
    {children}
  </HouseholdContext.Provider>;
}

export function useHouseholds() {
  const value = useContext(HouseholdContext);
  if (!value) throw new Error('useHouseholds requires HouseholdProvider');
  return value;
}
