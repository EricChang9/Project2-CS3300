import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

describe('development health check', () => {
  it('shows a connected backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }));
    render(<App />);
    expect(await screen.findByText('Backend connected')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/health', expect.objectContaining({ signal: expect.any(AbortSignal) }));
  });

  it('explains when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    render(<App />);
    expect(await screen.findByText(/Backend unavailable/)).toBeInTheDocument();
  });

  it('does not treat HTTP errors as a healthy backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
    render(<App />);
    expect(await screen.findByText(/Backend unavailable/)).toBeInTheDocument();
  });
});
