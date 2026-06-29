import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { BankConnection, ProviderInfo, SyncResult } from '../types'

const STATUS_STYLES: Record<BankConnection['status'], string> = {
  CONNECTED: 'bg-green-100 text-green-800',
  PENDING: 'bg-yellow-100 text-yellow-800',
  NEEDS_REAUTH: 'bg-orange-100 text-orange-800',
  ERROR: 'bg-red-100 text-red-800',
}

export default function Connections() {
  const queryClient = useQueryClient()

  const providersQuery = useQuery({
    queryKey: ['providers'],
    queryFn: () => api.get<ProviderInfo[]>('/connections/providers'),
  })
  const connectionsQuery = useQuery({
    queryKey: ['connections'],
    queryFn: () => api.get<BankConnection[]>('/connections'),
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['connections'] })
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    queryClient.invalidateQueries({ queryKey: ['insights'] })
  }

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-semibold mb-2">Connections</h2>
        <p className="text-gray-500">
          Link a bank or brokerage to sync transactions automatically — no CSV upload needed.
        </p>
      </div>

      <section>
        <h3 className="text-lg font-semibold mb-3">Available providers</h3>
        {providersQuery.isLoading && <p className="text-gray-500">Loading…</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          {providersQuery.data?.map((provider) => (
            <ProviderCard key={provider.id} provider={provider} onConnected={refresh} />
          ))}
        </div>
      </section>

      <section>
        <h3 className="text-lg font-semibold mb-3">Linked accounts</h3>
        {connectionsQuery.data && connectionsQuery.data.length === 0 && (
          <p className="text-gray-500">No accounts linked yet — connect one above.</p>
        )}
        <div className="space-y-3">
          {connectionsQuery.data?.map((connection) => (
            <ConnectionRow key={connection.id} connection={connection} onChange={refresh} />
          ))}
        </div>
      </section>
    </div>
  )
}

function ProviderCard({ provider, onConnected }: { provider: ProviderInfo; onConnected: () => void }) {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between">
        <span className="font-medium">{provider.displayName}</span>
        {!provider.configured && (
          <span className="text-xs text-gray-400">server not configured</span>
        )}
      </div>
      <p className="text-sm text-gray-500">{provider.description}</p>
      {provider.authType === 'OAUTH2' && <MonzoConnect configured={provider.configured} />}
      {provider.authType === 'API_KEY' && (
        <Trading212Connect providerId={provider.id} onConnected={onConnected} />
      )}
      {provider.authType === 'AGGREGATOR' && (
        <PlaidConnect provider={provider} onConnected={onConnected} />
      )}
    </div>
  )
}

function MonzoConnect({ configured }: { configured: boolean }) {
  const [error, setError] = useState<string | null>(null)
  const start = async () => {
    setError(null)
    try {
      const { url } = await api.get<{ url: string }>('/connections/monzo/authorize')
      window.location.href = url
    } catch (e) {
      setError((e as Error).message)
    }
  }
  return (
    <div>
      <button
        type="button"
        disabled={!configured}
        onClick={start}
        className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40"
      >
        Connect with Monzo
      </button>
      {error && <p className="text-red-700 text-sm mt-2">{error}</p>}
    </div>
  )
}

function Trading212Connect({ providerId, onConnected }: { providerId: string; onConnected: () => void }) {
  const [apiKey, setApiKey] = useState('')
  const mutation = useMutation({
    mutationFn: () => api.post('/connections/trading212', { apiKey, displayName: 'Trading 212' }),
    onSuccess: () => {
      setApiKey('')
      onConnected()
    },
  })
  void providerId
  return (
    <div className="space-y-2">
      <input
        type="password"
        value={apiKey}
        onChange={(e) => setApiKey(e.target.value)}
        placeholder="Paste your Trading 212 API key"
        className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm"
      />
      <button
        type="button"
        disabled={!apiKey || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40"
      >
        {mutation.isPending ? 'Saving…' : 'Save API key'}
      </button>
      <p className="text-xs text-gray-400">Generate a key in the Trading 212 app → Settings → API.</p>
      {mutation.isError && <p className="text-red-700 text-sm">{(mutation.error as Error).message}</p>}
    </div>
  )
}

function PlaidConnect({ provider, onConnected }: { provider: ProviderInfo; onConnected: () => void }) {
  const [message, setMessage] = useState<string | null>(null)
  const requestLinkToken = async () => {
    setMessage(null)
    try {
      const res = await api.post<{ linkToken: string | null; error: string | null }>(
        '/connections/plaid/link-token',
        { provider: provider.id },
      )
      if (res.error) {
        setMessage(res.error)
        return
      }
      // Opening the Plaid Link modal needs the Plaid Link SDK (@plaid/react-plaid-link); once it
      // returns a public_token, POST it to /connections/plaid/exchange to finish linking.
      setMessage(`Link token ready (${res.linkToken?.slice(0, 12)}…). Wire up Plaid Link to continue.`)
      onConnected()
    } catch (e) {
      setMessage((e as Error).message)
    }
  }
  return (
    <div className="space-y-2">
      <button
        type="button"
        disabled={!provider.configured}
        onClick={requestLinkToken}
        className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40"
      >
        Connect via Plaid
      </button>
      {message && <p className="text-sm text-gray-600">{message}</p>}
    </div>
  )
}

function ConnectionRow({ connection, onChange }: { connection: BankConnection; onChange: () => void }) {
  const syncMutation = useMutation({
    mutationFn: () => api.post<SyncResult>(`/connections/${connection.id}/sync`),
    onSuccess: onChange,
  })
  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/connections/${connection.id}`),
    onSuccess: onChange,
  })

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between gap-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-medium">{connection.displayName}</span>
          <span className={`text-xs px-2 py-0.5 rounded font-medium ${STATUS_STYLES[connection.status]}`}>
            {connection.status}
          </span>
        </div>
        <p className="text-xs text-gray-500 mt-0.5">
          {connection.lastSyncedAt
            ? `Last synced ${new Date(connection.lastSyncedAt).toLocaleString()}`
            : 'Never synced'}
        </p>
        {connection.lastError && <p className="text-xs text-red-700 mt-0.5 truncate">{connection.lastError}</p>}
        {syncMutation.data && syncMutation.data.success && (
          <p className="text-xs text-green-700 mt-0.5">Imported {syncMutation.data.imported} new transactions</p>
        )}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          disabled={syncMutation.isPending}
          onClick={() => syncMutation.mutate()}
          className="px-3 py-1.5 rounded-md text-sm font-medium bg-gray-900 text-white disabled:opacity-40"
        >
          {syncMutation.isPending ? 'Syncing…' : 'Sync now'}
        </button>
        <button
          type="button"
          onClick={() => deleteMutation.mutate()}
          className="px-3 py-1.5 rounded-md text-sm font-medium text-gray-600 hover:bg-gray-100"
        >
          Remove
        </button>
      </div>
    </div>
  )
}
