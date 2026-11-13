import { useEffect, useState } from 'react';

export default function App() {
  const [status, setStatus] = useState('Checking backend…');

  useEffect(() => {
    const controller = new AbortController();
    async function checkHealth() {
      try {
        const response = await fetch('/api/health', { signal: controller.signal });
        if (!response.ok) throw new Error('Health check failed');
        const health = await response.json();
        setStatus(health.status === 'UP' ? 'Backend connected' : 'Backend unavailable');
      } catch (error) {
        if (error.name !== 'AbortError') setStatus('Backend unavailable. Start the Spring Boot server.');
      }
    }
    checkHealth();
    return () => controller.abort();
  }, []);

  return (
    <main>
      <p className="eyebrow">Group 17 · Project 2</p>
      <h1>Roommate Expenses</h1>
      <p>Shared bills, clearer balances, and conversations in one place.</p>
      <p role="status">{status}</p>
      <section aria-labelledby="next-steps">
        <h2 id="next-steps">Coming next</h2>
        <ul>
          <li>Accounts and household invitations</li>
          <li>Equal and custom expense splits</li>
          <li>Payment requests and simulated settlements</li>
          <li>Email reminders, discussions, and monthly summaries</li>
        </ul>
      </section>
    </main>
  );
}
