import { useQuery } from '@tanstack/react-query'
import {
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { api } from '../api/client'
import { formatGBP } from '../util/format'
import type { CategorySpending, MonthlyAmount, MonthlyIncomeExpense } from '../types'

const COLORS = [
  '#3b82f6',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#ec4899',
  '#14b8a6',
  '#f97316',
]

export default function Dashboard() {
  const spendingQuery = useQuery({
    queryKey: ['insights', 'spending-by-category'],
    queryFn: () => api.get<CategorySpending[]>('/insights/spending-by-category'),
  })

  const trendQuery = useQuery({
    queryKey: ['insights', 'monthly-trend'],
    queryFn: () => api.get<MonthlyAmount[]>('/insights/monthly-trend?months=12'),
  })

  const incomeExpenseQuery = useQuery({
    queryKey: ['insights', 'income-vs-expenses'],
    queryFn: () => api.get<MonthlyIncomeExpense[]>('/insights/income-vs-expenses?months=12'),
  })

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-semibold">Dashboard</h2>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Spending by category (this month)">
          {spendingQuery.isLoading ? (
            <Loading />
          ) : !spendingQuery.data || spendingQuery.data.length === 0 ? (
            <Empty message="No spend in this period yet." />
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={spendingQuery.data}
                  dataKey="total"
                  nameKey="category"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={2}
                >
                  {spendingQuery.data.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => (typeof value === 'number' ? formatGBP(value) : String(value ?? ''))} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card title="Monthly spending trend">
          {trendQuery.isLoading ? (
            <Loading />
          ) : !trendQuery.data ? (
            <Empty message="No data." />
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={trendQuery.data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="yearMonth" />
                <YAxis tickFormatter={(v: number) => `£${v}`} />
                <Tooltip formatter={(value) => (typeof value === 'number' ? formatGBP(value) : String(value ?? ''))} />
                <Line type="monotone" dataKey="total" stroke="#3b82f6" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card className="lg:col-span-2" title="Income vs expenses">
          {incomeExpenseQuery.isLoading ? (
            <Loading />
          ) : !incomeExpenseQuery.data ? (
            <Empty message="No data." />
          ) : (
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={incomeExpenseQuery.data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="yearMonth" />
                <YAxis tickFormatter={(v: number) => `£${v}`} />
                <Tooltip formatter={(value) => (typeof value === 'number' ? formatGBP(value) : String(value ?? ''))} />
                <Legend />
                <Bar dataKey="income" fill="#10b981" name="Income" />
                <Bar dataKey="expenses" fill="#ef4444" name="Expenses" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
      </div>
    </div>
  )
}

function Card({
  title,
  className,
  children,
}: {
  title: string
  className?: string
  children: React.ReactNode
}) {
  return (
    <div className={'bg-white border border-gray-200 rounded-lg p-4 ' + (className ?? '')}>
      <h3 className="font-semibold text-gray-900 mb-3">{title}</h3>
      {children}
    </div>
  )
}

function Loading() {
  return <p className="text-gray-500 py-10 text-center">Loading…</p>
}

function Empty({ message }: { message: string }) {
  return <p className="text-gray-500 py-10 text-center">{message}</p>
}
