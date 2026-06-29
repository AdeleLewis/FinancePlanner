import { useCallback, useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { usePlaidLink } from 'react-plaid-link'
import { api } from '../api/client'
import type { BankConnection, ProviderInfo, SyncResult } from '../types'

const STATUS_STYLES: Record<BankConnection['status'], string> = {
  CONNECTED: 'bg-emerald-100 text-emerald-900',
  PENDING: 'bg-amber-100 text-amber-900',
  NEEDS_REAUTH: 'bg-orange-100 text-orange-900',
  ERROR: 'bg-rose-100 text-rose-900',
}

const PROVIDER_BRAND: Record<string, { bg: string; fg: string; initials: string }> = {
  MONZO: { bg: '#FF4F40', fg: '#fff', initials: 'M' },
  TRADING212: { bg: '#00AAE4', fg: '#fff', initials: 'T' },
  SANTANDER: { bg: '#EC0000', fg: '#fff', initials: 'S' },
  AMEX: { bg: '#006FCF', fg: '#fff', initials: 'A' },
}

// Open Banking consent under PSD2 must be re-authorised every 90 days.
const CONSENT_WINDOW_DAYS = 90

type Toast = { id: number; kind: 'success' | 'error' | 'info'; message: string }

export default function Connections() {
  const queryClient = useQueryClient()
  const [toasts, setToasts] = useState<Toast[]>([])

  const pushToast = useCallback((kind: Toast['kind'], message: string) => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, kind, message }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4500)
  }, [])

  // Surface ?connected=monzo / ?error=… from the Monzo OAuth callback.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const connected = params.get('connected')
    const error = params.get('error')
    if (connected) pushToast('success', `${connected} connected successfully`)
    if (error) pushToast('error', `Connection failed: ${error}`)
    if (connected || error) {
      params.delete('connected')
      params.delete('error')
      const next = params.toString()
      window.history.replaceState({}, '', `${window.location.pathname}${next ? `?${next}` : ''}`)
    }
  }, [pushToast])

  const providersQuery = useQuery({
    queryKey: ['providers'],
    queryFn: () => api.get<ProviderInfo[]>('/connections/providers'),
  })
  const connectionsQuery = useQuery({
    queryKey: ['connections'],
    queryFn: () => api.get<BankConnection[]>('/connections'),
  })

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['connections'] })
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    queryClient.invalidateQueries({ queryKey: ['insights'] })
  }, [queryClient])

  const connections = connectionsQuery.data ?? []
  const hasConnections = connections.length > 0

  const syncAllMutation = useMutation({
    mutationFn: async () => {
      const results = await Promise.all(
        connections.map((c) => api.post<SyncResult>(`/connections/${c.id}/sync`)),
      )
      return results
    },
    onSuccess: (results) => {
      const imported = results.reduce((sum, r) => sum + (r.success ? r.imported : 0), 0)
      const failed = results.filter((r) => !r.success).length
      if (failed > 0) {
        pushToast('error', `${failed} sync${failed === 1 ? '' : 's'} failed — see status below`)
      } else {
        pushToast('success', `Synced ${results.length} accounts — ${imported} new transactions`)
      }
      refresh()
    },
  })

  return (
    <div className="space-y-8">
      <Toasts toasts={toasts} />

      <div>
        <h2 className="text-2xl font-semibold mb-2">Connections</h2>
        <p className="text-gray-500">
          Link a bank or brokerage to sync transactions automatically — no CSV upload needed.
        </p>
      </div>

      <section>
        <h3 className="text-lg font-semibold mb-3">Available providers</h3>
        {providersQuery.isLoading && <ProviderSkeleton />}
        <div className="grid gap-4 sm:grid-cols-2">
          {providersQuery.data?.map((provider) => (
            <ProviderCard
              key={provider.id}
              provider={provider}
              onConnected={refresh}
              onToast={pushToast}
            />
          ))}
        </div>
      </section>

      <section>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-lg font-semibold">Linked accounts</h3>
          {hasConnections && (
            <button
              type="button"
              onClick={() => syncAllMutation.mutate()}
              disabled={syncAllMutation.isPending}
              className="px-3 py-1.5 rounded-md text-sm font-medium border border-gray-300 hover:bg-gray-100 disabled:opacity-40 inline-flex items-center gap-2"
            >
              {syncAllMutation.isPending && <Spinner />}
              {syncAllMutation.isPending ? 'Syncing all…' : 'Sync all'}
            </button>
          )}
        </div>
        {connectionsQuery.isLoading && <ConnectionsSkeleton />}
        {!connectionsQuery.isLoading && !hasConnections && (
          <p className="text-gray-500">No accounts linked yet — connect one above.</p>
        )}
        <div className="space-y-3">
          {connections.map((connection) => (
            <ConnectionRow
              key={connection.id}
              connection={connection}
              onChange={refresh}
              onToast={pushToast}
            />
          ))}
        </div>
      </section>
    </div>
  )
}

function ProviderCard({
  provider,
  onConnected,
  onToast,
}: {
  provider: ProviderInfo
  onConnected: () => void
  onToast: (kind: Toast['kind'], message: string) => void
}) {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2.5 min-w-0">
          <ProviderAvatar providerId={provider.id} />
          <span className="font-medium truncate">{provider.displayName}</span>
        </div>
        {!provider.configured && (
          <span
            className="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded shrink-0"
            title="Set the relevant environment variables on the server to enable this provider"
          >
            server not configured
          </span>
        )}
      </div>
      <p className="text-sm text-gray-500">{provider.description}</p>
      {provider.authType === 'OAUTH2' && (
        <MonzoConnect configured={provider.configured} onToast={onToast} />
      )}
      {provider.authType === 'API_KEY' && (
        <Trading212Connect onConnected={onConnected} onToast={onToast} />
      )}
      {provider.authType === 'AGGREGATOR' && (
        <PlaidConnect provider={provider} onConnected={onConnected} onToast={onToast} />
      )}
    </div>
  )
}

function ProviderAvatar({ providerId }: { providerId: string }) {
  const brand = PROVIDER_BRAND[providerId] ?? { bg: '#6b7280', fg: '#fff', initials: '?' }
  return (
    <span
      aria-hidden="true"
      className="inline-flex items-center justify-center h-8 w-8 rounded-full text-sm font-semibold shrink-0"
      style={{ background: brand.bg, color: brand.fg }}
    >
      {brand.initials}
    </span>
  )
}

function MonzoConnect({
  configured,
  onToast,
}: {
  configured: boolean
  onToast: (kind: Toast['kind'], message: string) => void
}) {
  const [pending, setPending] = useState(false)
  const start = async () => {
    setPending(true)
    try {
      const { url } = await api.get<{ url: string }>('/connections/monzo/authorize')
      window.location.href = url
    } catch (e) {
      setPending(false)
      onToast('error', (e as Error).message)
    }
  }
  return (
    <button
      type="button"
      disabled={!configured || pending}
      onClick={start}
      className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40 inline-flex items-center gap-2"
    >
      {pending && <Spinner />}
      Connect with Monzo
    </button>
  )
}

function Trading212Connect({
  onConnected,
  onToast,
}: {
  onConnected: () => void
  onToast: (kind: Toast['kind'], message: string) => void
}) {
  const [apiKey, setApiKey] = useState('')
  const mutation = useMutation({
    mutationFn: () => api.post('/connections/trading212', { apiKey, displayName: 'Trading 212' }),
    onSuccess: () => {
      setApiKey('')
      onToast('success', 'Trading 212 connected')
      onConnected()
    },
    onError: (e) => onToast('error', (e as Error).message),
  })
  return (
    <div className="space-y-2">
      <label className="sr-only" htmlFor="t212-key">
        Trading 212 API key
      </label>
      <input
        id="t212-key"
        type="password"
        value={apiKey}
        onChange={(e) => setApiKey(e.target.value)}
        placeholder="Paste your Trading 212 API key"
        autoComplete="off"
        spellCheck={false}
        className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm font-mono"
      />
      <button
        type="button"
        disabled={!apiKey || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40 inline-flex items-center gap-2"
      >
        {mutation.isPending && <Spinner />}
        {mutation.isPending ? 'Saving…' : 'Save API key'}
      </button>
      <p className="text-xs text-gray-500">
        Generate a key in the Trading 212 app → Settings → API.{' '}
        <span className="text-gray-400">Stored on this server only — never sent elsewhere.</span>
      </p>
    </div>
  )
}

function PlaidConnect({
  provider,
  onConnected,
  onToast,
}: {
  provider: ProviderInfo
  onConnected: () => void
  onToast: (kind: Toast['kind'], message: string) => void
}) {
  const [linkToken, setLinkToken] = useState<string | null>(null)
  const [fetching, setFetching] = useState(false)

  const exchange = useMutation({
    mutationFn: (publicToken: string) =>
      api.post('/connections/plaid/exchange', {
        provider: provider.id,
        publicToken,
        displayName: provider.displayName,
      }),
    onSuccess: () => {
      onToast('success', `${provider.displayName} connected`)
      onConnected()
      setLinkToken(null)
    },
    onError: (e) => onToast('error', (e as Error).message),
  })

  const { open, ready } = usePlaidLink({
    token: linkToken,
    onSuccess: (publicToken) => exchange.mutate(publicToken),
    onExit: (err) => {
      if (err) onToast('error', err.display_message ?? err.error_message ?? 'Plaid Link aborted')
      setLinkToken(null)
    },
  })

  // Once we have a link token and Plaid Link is ready, open the modal.
  useEffect(() => {
    if (linkToken && ready) open()
  }, [linkToken, ready, open])

  const start = async () => {
    setFetching(true)
    try {
      const res = await api.post<{ linkToken: string | null; error: string | null }>(
        '/connections/plaid/link-token',
        { provider: provider.id },
      )
      if (res.error || !res.linkToken) {
        onToast('error', res.error ?? 'Could not create a Plaid Link token')
        return
      }
      setLinkToken(res.linkToken)
    } catch (e) {
      onToast('error', (e as Error).message)
    } finally {
      setFetching(false)
    }
  }

  const pending = fetching || exchange.isPending
  return (
    <button
      type="button"
      disabled={!provider.configured || pending}
      onClick={start}
      className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40 inline-flex items-center gap-2"
    >
      {pending && <Spinner />}
      Connect via Plaid
    </button>
  )
}

function ConnectionRow({
  connection,
  onChange,
  onToast,
}: {
  connection: BankConnection
  onChange: () => void
  onToast: (kind: Toast['kind'], message: string) => void
}) {
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const syncMutation = useMutation({
    mutationFn: () => api.post<SyncResult>(`/connections/${connection.id}/sync`),
    onSuccess: (result) => {
      if (result.success) {
        onToast('success', `Imported ${result.imported} new transactions from ${connection.displayName}`)
      } else {
        onToast('error', result.error ?? 'Sync failed')
      }
      onChange()
    },
    onError: (e) => onToast('error', (e as Error).message),
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/connections/${connection.id}`),
    onSuccess: () => {
      onToast('info', `${connection.displayName} removed`)
      onChange()
    },
    onError: (e) => onToast('error', (e as Error).message),
  })

  // Reset confirm state if the user wanders off without clicking through.
  useEffect(() => {
    if (!confirmingDelete) return
    const timer = setTimeout(() => setConfirmingDelete(false), 4000)
    return () => clearTimeout(timer)
  }, [confirmingDelete])

  const reauthHint = useReauthHint(connection)

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between gap-4">
      <div className="min-w-0 flex items-center gap-3">
        <ProviderAvatar providerId={connection.provider} />
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-medium">{connection.displayName}</span>
            <span
              className={`text-xs px-2 py-0.5 rounded font-medium ${STATUS_STYLES[connection.status]}`}
              role="status"
            >
              {humanStatus(connection.status)}
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-0.5">
            {connection.lastSyncedAt ? (
              <span title={new Date(connection.lastSyncedAt).toLocaleString()}>
                Last synced {relativeTime(connection.lastSyncedAt)}
              </span>
            ) : (
              'Never synced'
            )}
          </p>
          {reauthHint && (
            <p className={`text-xs mt-0.5 ${reauthHint.urgent ? 'text-orange-700' : 'text-gray-500'}`}>
              {reauthHint.message}
            </p>
          )}
          {connection.lastError && (
            <p className="text-xs text-rose-700 mt-0.5 truncate" title={connection.lastError}>
              {connection.lastError}
            </p>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          disabled={syncMutation.isPending}
          onClick={() => syncMutation.mutate()}
          className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40 inline-flex items-center gap-2"
          aria-label={`Sync ${connection.displayName} now`}
        >
          {syncMutation.isPending && <Spinner />}
          {syncMutation.isPending ? 'Syncing…' : 'Sync now'}
        </button>
        <button
          type="button"
          disabled={deleteMutation.isPending}
          onClick={() => {
            if (confirmingDelete) {
              deleteMutation.mutate()
              setConfirmingDelete(false)
            } else {
              setConfirmingDelete(true)
            }
          }}
          className={`px-3 py-1.5 rounded-md text-sm font-medium disabled:opacity-40 ${
            confirmingDelete ? 'bg-rose-600 text-white' : 'text-gray-600 hover:bg-gray-100'
          }`}
          aria-label={confirmingDelete ? `Confirm removing ${connection.displayName}` : `Remove ${connection.displayName}`}
        >
          {confirmingDelete ? 'Click to confirm' : 'Remove'}
        </button>
      </div>
    </div>
  )
}

function useReauthHint(connection: BankConnection) {
  return useMemo(() => {
    if (connection.authType !== 'AGGREGATOR') return null
    const expires = new Date(connection.createdAt).getTime() + CONSENT_WINDOW_DAYS * 86_400_000
    const days = Math.ceil((expires - Date.now()) / 86_400_000)
    if (days <= 0) {
      return { urgent: true, message: 'Open Banking consent has expired — re-authorise to keep syncing.' }
    }
    if (days <= 14) {
      return { urgent: true, message: `Re-authorise needed in ${days} day${days === 1 ? '' : 's'} (Open Banking 90-day rule).` }
    }
    return { urgent: false, message: `Open Banking consent renews in ${days} days.` }
  }, [connection.authType, connection.createdAt])
}

function humanStatus(status: BankConnection['status']) {
  switch (status) {
    case 'NEEDS_REAUTH':
      return 'Re-auth required'
    case 'CONNECTED':
      return 'Connected'
    case 'PENDING':
      return 'Pending'
    case 'ERROR':
      return 'Error'
  }
}

function relativeTime(iso: string): string {
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (diffSec < 30) return 'just now'
  if (diffSec < 60) return `${diffSec} seconds ago`
  const mins = Math.round(diffSec / 60)
  if (mins < 60) return `${mins} minute${mins === 1 ? '' : 's'} ago`
  const hrs = Math.round(mins / 60)
  if (hrs < 24) return `${hrs} hour${hrs === 1 ? '' : 's'} ago`
  const days = Math.round(hrs / 24)
  if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`
  return new Date(iso).toLocaleDateString()
}

function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="inline-block h-3.5 w-3.5 rounded-full border-2 border-current border-r-transparent animate-spin"
    />
  )
}

function ProviderSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="bg-white border border-gray-200 rounded-lg p-4 space-y-3 animate-pulse">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-full bg-gray-200" />
            <div className="h-4 bg-gray-200 rounded w-1/3" />
          </div>
          <div className="h-3 bg-gray-100 rounded w-3/4" />
          <div className="h-8 bg-gray-100 rounded w-32" />
        </div>
      ))}
    </div>
  )
}

function ConnectionsSkeleton() {
  return (
    <div className="space-y-3">
      {[0, 1].map((i) => (
        <div key={i} className="bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between animate-pulse">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-full bg-gray-200" />
            <div className="space-y-2">
              <div className="h-4 bg-gray-200 rounded w-32" />
              <div className="h-3 bg-gray-100 rounded w-44" />
            </div>
          </div>
          <div className="h-8 bg-gray-100 rounded w-28" />
        </div>
      ))}
    </div>
  )
}

function Toasts({ toasts }: { toasts: Toast[] }) {
  if (toasts.length === 0) return null
  return (
    <div
      className="fixed top-4 right-4 z-50 space-y-2 max-w-sm"
      role="region"
      aria-live="polite"
      aria-label="Notifications"
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`rounded-md shadow-lg px-4 py-2.5 text-sm font-medium border ${
            toast.kind === 'success'
              ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
              : toast.kind === 'error'
              ? 'bg-rose-50 border-rose-200 text-rose-900'
              : 'bg-gray-50 border-gray-200 text-gray-900'
          }`}
        >
          {toast.message}
        </div>
      ))}
    </div>
  )
}
