import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Logo } from '../components/Logo';

const DEMO = { email: 'demo@applytrack.dev', password: 'demo1234' };

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  if (auth.isAuthenticated) return <Navigate to="/board" replace />;

  const from = (location.state as { from?: string } | null)?.from ?? '/board';

  const run = async (action: () => Promise<void>) => {
    setPending(true);
    setError(null);
    setFieldErrors({});
    try {
      await action();
      navigate(from, { replace: true });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) setFieldErrors(e.fieldErrors);
      else setError(e instanceof Error ? e.message : 'Something went wrong');
    } finally {
      setPending(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void run(() =>
      mode === 'login' ? auth.login(email, password) : auth.register(email, password, displayName),
    );
  };

  return (
    <div className="auth-page">
      <div className="auth-intro">
        <Logo />
        <h1>Every application, one calm place.</h1>
        <p className="muted">
          Track roles from wishlist to offer on a drag-and-drop board, keep a timeline of every status change, and
          never miss a follow-up.
        </p>
      </div>

      <form className="auth-card" onSubmit={submit} noValidate>
        <h2>{mode === 'login' ? 'Sign in' : 'Create your account'}</h2>

        {mode === 'register' && (
          <label className="field">
            <span>Name</span>
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="name" required />
            {fieldErrors.displayName && <span className="field-error">{fieldErrors.displayName}</span>}
          </label>
        )}
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            required
          />
          {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
        </label>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
          {pending ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>

        {mode === 'login' && (
          <button
            type="button"
            className="btn btn-ghost btn-block"
            disabled={pending}
            onClick={() => void run(() => auth.login(DEMO.email, DEMO.password))}
          >
            Explore with the demo account
          </button>
        )}

        <p className="auth-switch muted small">
          {mode === 'login' ? (
            <>
              New here? <Link to="/register">Create an account</Link>
            </>
          ) : (
            <>
              Already have an account? <Link to="/login">Sign in</Link>
            </>
          )}
        </p>
      </form>
    </div>
  );
}
