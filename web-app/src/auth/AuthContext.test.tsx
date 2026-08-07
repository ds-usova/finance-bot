import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { createSession, deleteSession, readSession } from '../api/session';
import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';

vi.mock('../api/session', () => ({
  createSession: vi.fn(),
  readSession: vi.fn(),
  deleteSession: vi.fn(),
}));

const readSessionMock = vi.mocked(readSession);
const createSessionMock = vi.mocked(createSession);
const deleteSessionMock = vi.mocked(deleteSession);

function Probe() {
  const { status, session, signIn, signOut } = useAuth();
  return (
    <div>
      <p>status: {status}</p>
      <p>id: {session?.externalId ?? 'none'}</p>
      <button type="button" onClick={() => void signIn({ id: '42' })}>
        sign in
      </button>
      <button type="button" onClick={() => void signOut()}>
        sign out
      </button>
    </div>
  );
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

describe('the auth provider', () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it('starts loading, so nothing decides on a session it has not read yet', () => {
    readSessionMock.mockReturnValue(new Promise(() => {}));

    renderProbe();

    expect(screen.getByText('status: loading')).toBeInTheDocument();
  });

  it('becomes authenticated when a session is already open', async () => {
    readSessionMock.mockResolvedValue({ externalId: '987654321' });

    renderProbe();

    await waitFor(() => expect(screen.getByText('status: authenticated')).toBeInTheDocument());
    expect(screen.getByText('id: 987654321')).toBeInTheDocument();
  });

  it('becomes anonymous when the session read is refused, without surfacing an error', async () => {
    readSessionMock.mockRejectedValue(new ApiError(401, 'no session'));

    renderProbe();

    await waitFor(() => expect(screen.getByText('status: anonymous')).toBeInTheDocument());
  });

  it('becomes authenticated after a successful sign-in', async () => {
    readSessionMock.mockResolvedValue(null);
    createSessionMock.mockResolvedValue({ externalId: '42' });

    renderProbe();
    await waitFor(() => expect(screen.getByText('status: anonymous')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'sign in' }));

    await waitFor(() => expect(screen.getByText('status: authenticated')).toBeInTheDocument());
    expect(screen.getByText('id: 42')).toBeInTheDocument();
  });

  it('becomes anonymous again after signing out', async () => {
    readSessionMock.mockResolvedValue({ externalId: '42' });
    deleteSessionMock.mockResolvedValue(undefined);

    renderProbe();
    await waitFor(() => expect(screen.getByText('status: authenticated')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'sign out' }));

    await waitFor(() => expect(screen.getByText('status: anonymous')).toBeInTheDocument());
    expect(deleteSessionMock).toHaveBeenCalledOnce();
  });
});

describe('the auth hook', () => {
  it('refuses to be used outside a provider rather than answering with nothing', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => render(<Probe />)).toThrow(/outside an AuthProvider/);
  });
});
