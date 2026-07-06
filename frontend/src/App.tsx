import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from './api/client'
import Dashboard from './pages/Dashboard'
import Upload from './pages/Upload'
import Savings from './pages/Savings'
import Connections from './pages/Connections'

type Tab = 'dashboard' | 'upload' | 'connections' | 'savings'

function initialTab(): Tab {
  // Honour ?tab=connections, used by the Monzo OAuth callback redirect.
  const requested = new URLSearchParams(window.location.search).get('tab')
  return requested === 'connections' || requested === 'upload' || requested === 'savings'
    ? requested
    : 'dashboard'
}

function App() {
  const [tab, setTab] = useState<Tab>(initialTab)

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="sticky top-0 z-10 bg-white/95 backdrop-blur border-b border-gray-200">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center gap-6">
          <h1 className="text-xl font-semibold">Budget</h1>
          <nav className="flex gap-2">
            <TabButton active={tab === 'dashboard'} onClick={() => setTab('dashboard')}>
              Dashboard
            </TabButton>
            <TabButton active={tab === 'upload'} onClick={() => setTab('upload')}>
              Upload
            </TabButton>
            <TabButton active={tab === 'connections'} onClick={() => setTab('connections')}>
              Connections
            </TabButton>
            <TabButton active={tab === 'savings'} onClick={() => setTab('savings')}>
              Savings
            </TabButton>
          </nav>
          <LockButton />
        </div>
      </header>
      <main className="max-w-6xl mx-auto px-6 py-8">
        {tab === 'dashboard' && <Dashboard />}
        {tab === 'upload' && <Upload />}
        {tab === 'connections' && <Connections />}
        {tab === 'savings' && <Savings />}
      </main>
    </div>
  )
}

function LockButton() {
  const queryClient = useQueryClient()
  const lock = async () => {
    await api.post('/auth/logout')
    queryClient.setQueryData(['auth', 'status'], { setupRequired: false, authenticated: false })
    queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'auth' })
  }
  return (
    <button
      type="button"
      onClick={lock}
      className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium text-gray-600 hover:bg-gray-100"
      title="Lock the app — you'll need your password to get back in"
    >
      <svg
        className="h-4 w-4"
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
      Lock
    </button>
  )
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        'px-3 py-1.5 rounded-md text-sm font-medium transition-colors ' +
        (active ? 'bg-gray-900 text-white' : 'text-gray-600 hover:bg-gray-100')
      }
    >
      {children}
    </button>
  )
}

export default App
