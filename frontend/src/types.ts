export type Transaction = {
  id: number
  date: string
  description: string
  amount: number
  category: string
  statementId: number | null
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
