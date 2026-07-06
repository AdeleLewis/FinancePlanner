import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, AUTH_EXPIRED_EVENT } from '../api/client'

type AuthStatus = { setupRequired: boolean; authenticated: boolean }

// Gates the whole app behind the session: first run shows the create-password
// screen, a signed-out session shows login, and a 401 from any API call flips
// back to login without losing the page.
export function AuthGate({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient()

  const statusQuery = useQuery({
    queryKey: ['auth', 'status'],
    queryFn: () => api.get<AuthStatus>('/auth/status'),
    staleTime: 60_000,
    retry: 1,
  })

  useEffect(() => {
    const onExpired = () => {
      queryClient.setQueryData<AuthStatus>(['auth', 'status'], (previous) =>
        previous ? { ...previous, authenticated: false } : previous,
      )
      queryClient.invalidateQueries({ queryKey: ['auth', 'status'] })
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired)
  }, [queryClient])

  if (statusQuery.isLoading) {
    return <Shell />
  }
  if (statusQuery.isError || !statusQuery.data) {
    return (
      <Shell>
        <p className="text-sm text-gray-600">Can't reach the server — is the backend running?</p>
        <button
          type="button"
          onClick={() => statusQuery.refetch()}
          className="mt-3 bg-gray-900 text-white px-4 py-2 rounded-md text-sm font-medium"
        >
          Retry
        </button>
      </Shell>
    )
  }
  if (statusQuery.data.setupRequired) {
    return <SetupScreen />
  }
  if (!statusQuery.data.authenticated) {
    return <LoginScreen />
  }
  return children
}

function SetupScreen() {
  const queryClient = useQueryClient()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [mismatch, setMismatch] = useState(false)

  const setup = useMutation({
    mutationFn: () => api.post<AuthStatus>('/auth/setup', { password }),
    onSuccess: (status) => queryClient.setQueryData(['auth', 'status'], status),
  })

  return (
    <Shell>
      <AuthCard
        title="Create a password"
        subtitle="This protects everything in the app. It can't be recovered — if you forget it, you'll have to reset the database."
        onSubmit={() => {
          const bad = password !== confirm
          setMismatch(bad)
          if (!bad) setup.mutate()
        }}
        error={mismatch ? "Passwords don't match" : setup.isError ? (setup.error as Error).message : null}
        submitLabel={setup.isPending ? 'Setting up…' : 'Set password and enter'}
        pending={setup.isPending}
      >
        <PasswordField
          id="new-password"
          label="Password (at least 8 characters)"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
        />
        <PasswordField
          id="confirm-password"
          label="Confirm password"
          value={confirm}
          onChange={setConfirm}
          autoComplete="new-password"
        />
      </AuthCard>
    </Shell>
  )
}

function LoginScreen() {
  const queryClient = useQueryClient()
  const [password, setPassword] = useState('')

  const login = useMutation({
    mutationFn: () => api.post<AuthStatus>('/auth/login', { password }),
    onSuccess: (status) => {
      setPassword('')
      queryClient.setQueryData(['auth', 'status'], status)
      // Session changed — anything cached belongs to the previous session's view.
      queryClient.invalidateQueries()
    },
  })

  return (
    <Shell>
      <AuthCard
        title="Unlock Budget"
        subtitle="Enter your password to open your finances."
        onSubmit={() => login.mutate()}
        error={login.isError ? loginErrorMessage(login.error) : null}
        submitLabel={login.isPending ? 'Unlocking…' : 'Unlock'}
        pending={login.isPending}
      >
        <PasswordField
          id="current-password"
          label="Password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          autoFocus
        />
      </AuthCard>
    </Shell>
  )
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) return 'Incorrect password.'
  if (error instanceof ApiError && error.status === 429) {
    return 'Too many failed attempts — wait a moment and try again.'
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}

function AuthCard({
  title,
  subtitle,
  error,
  submitLabel,
  pending,
  onSubmit,
  children,
}: {
  title: string
  subtitle: string
  error: string | null
  submitLabel: string
  pending: boolean
  onSubmit: () => void
  children: React.ReactNode
}) {
  return (
    <div className="w-full max-w-sm">
      <div className="flex items-center gap-2.5 justify-center mb-6">
        <LockIcon />
        <span className="text-xl font-semibold text-gray-900">Budget</span>
      </div>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          onSubmit()
        }}
        className="bg-white border border-gray-200 rounded-xl shadow-sm p-6 space-y-4"
      >
        <div>
          <h1 className="text-lg font-semibold text-gray-900">{title}</h1>
          <p className="text-sm text-gray-500 mt-1">{subtitle}</p>
        </div>
        {children}
        {error && <p className="text-sm text-red-700">{error}</p>}
        <button
          type="submit"
          disabled={pending}
          className="w-full bg-gray-900 text-white px-4 py-2 rounded-md text-sm font-medium disabled:opacity-50"
        >
          {submitLabel}
        </button>
      </form>
      <p className="text-xs text-gray-400 text-center mt-4">
        Only a one-way hash of your password is stored — never the password itself.
      </p>
    </div>
  )
}

function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  autoFocus,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  autoFocus?: boolean
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm text-gray-600 mb-1">
        {label}
      </label>
      <input
        id={id}
        type="password"
        required
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        className="w-full border border-gray-300 rounded-md px-3 py-2"
      />
    </div>
  )
}

function Shell({ children }: { children?: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center px-6 text-center">
      {children}
    </div>
  )
}

function LockIcon() {
  return (
    <svg
      className="h-5 w-5 text-gray-900"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 018 0v4" />
    </svg>
  )
}
