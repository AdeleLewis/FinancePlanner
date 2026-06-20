import { useState } from 'react'
import Dashboard from './pages/Dashboard'
import Upload from './pages/Upload'
import Savings from './pages/Savings'

type Tab = 'dashboard' | 'upload' | 'savings'

function App() {
  const [tab, setTab] = useState<Tab>('dashboard')

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center gap-6">
          <h1 className="text-xl font-semibold">Budget</h1>
          <nav className="flex gap-2">
            <TabButton active={tab === 'dashboard'} onClick={() => setTab('dashboard')}>
              Dashboard
            </TabButton>
            <TabButton active={tab === 'upload'} onClick={() => setTab('upload')}>
              Upload
            </TabButton>
            <TabButton active={tab === 'savings'} onClick={() => setTab('savings')}>
              Savings
            </TabButton>
          </nav>
        </div>
      </header>
      <main className="max-w-6xl mx-auto px-6 py-8">
        {tab === 'dashboard' && <Dashboard />}
        {tab === 'upload' && <Upload />}
        {tab === 'savings' && <Savings />}
      </main>
    </div>
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
