import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider, STORAGE_KEY, useAuth } from './AuthContext';

function WhoAmI() {
  const { user } = useAuth();
  return <p>{user ? `Signed in as ${user.displayName}` : 'Signed out'}</p>;
}

function session(id: number, displayName: string) {
  return JSON.stringify({
    token: `token-${id}`,
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id, email: `${displayName.toLowerCase()}@example.com`, displayName },
  });
}

/** What the browser does in the other tabs when one tab writes to localStorage. */
function storageChangedInAnotherTab(value: string | null) {
  act(() => {
    if (value === null) localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, value);
    window.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEY, newValue: value }));
  });
}

function renderApp() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <WhoAmI />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('AuthProvider across tabs', () => {
  afterEach(() => localStorage.clear());

  it('logs out when another tab logs out', () => {
    localStorage.setItem(STORAGE_KEY, session(1, 'Hamza'));
    renderApp();
    expect(screen.getByText('Signed in as Hamza')).toBeInTheDocument();

    storageChangedInAnotherTab(null);

    expect(screen.getByText('Signed out')).toBeInTheDocument();
  });

  it('picks up a login from another tab and ignores unrelated keys', () => {
    renderApp();

    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: 'something-else', newValue: 'x' }));
    });
    expect(screen.getByText('Signed out')).toBeInTheDocument();

    storageChangedInAnotherTab(session(2, 'Sara'));
    expect(screen.getByText('Signed in as Sara')).toBeInTheDocument();
  });
});
