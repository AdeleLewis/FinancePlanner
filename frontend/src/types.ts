export type Transaction = {
  id: number
  date: string
  description: string
  amount: number
  category: string
  statementId: number | null
  source?: string | null
  externalId?: string | null
}

export type ProviderInfo = {
  id: string
  displayName: string
  authType: 'OAUTH2' | 'API_KEY' | 'AGGREGATOR'
  description: string
  configured: boolean
}

export type BankConnection = {
  id: number
  provider: string
  providerName: string
  displayName: string
  authType: 'OAUTH2' | 'API_KEY' | 'AGGREGATOR'
  status: 'PENDING' | 'CONNECTED' | 'NEEDS_REAUTH' | 'ERROR'
  lastSyncedAt: string | null
  lastError: string | null
  createdAt: string
}

export type SyncResult = {
  connectionId: number
  imported: number
  success: boolean
  error: string | null
}

export type Statement = {
  id: number
  uploadedAt: string
  originalFilename: string
  rowCount: number
}

export type UploadResult = {
  statementId: number
  rowCount: number
}

export type CategorySpending = {
  category: string
  total: number
}

export type MonthlyAmount = {
  yearMonth: string
  total: number
}

export type MonthlyIncomeExpense = {
  yearMonth: string
  income: number
  expenses: number
}

export type SavingsSnapshot = {
  id: number
  recordedAt: string
  amount: number
  accountName: string | null
}

export type SavingsGoal = {
  id: number
  name: string
  targetAmount: number
  targetDate: string
}

export type Recommendation = {
  currentSavings: number
  targetAmount: number
  gap: number
  monthsRemaining: number
  monthlyNeeded: number
  avgMonthlyDisposable: number
  onTrack: boolean
  suggestedCategoriesToCut: string[]
}
