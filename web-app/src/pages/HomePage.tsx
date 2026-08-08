import { useAuth } from '../auth/useAuth';

export function HomePage() {
  const { session, signOut } = useAuth();

  return (
    <main>
      <h1>Finance Bot</h1>
      <p>Signed in as {session?.externalId}</p>
      <button type="button" onClick={() => void signOut()}>
        Sign out
      </button>
    </main>
  );
}
