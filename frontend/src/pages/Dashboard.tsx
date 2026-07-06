import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../api/client'
import { SubscriptionsCard } from '../components/SubscriptionsCard'
import { ChartCard, ChartEmpty, ChartSkeleton } from '../charts/ChartCard'
import { MoneyTooltip } from '../charts/ChartTooltip'
import { StatTile } from '../charts/StatTile'
import { axisTick, chartColors, niceMoneyTicks } from '../charts/theme'
import { formatGBP, formatGBPCompact, formatMonthLong, formatMonthShort } from '../util/format'
import type { CategorySpending, MonthlyAmount, MonthlyIncomeExpense } from '../types'

const MAX_CATEGORY_BARS = 8

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

      <KpiRow
        incomeExpense={incomeExpenseQuery.data}
        trend={trendQuery.data}
        spending={spendingQuery.data}
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <ChartCard title="Spending by category" subtitle="This month, largest first">
          {spendingQuery.isLoading ? (
            <ChartSkeleton height={280} />
          ) : !spendingQuery.data || spendingQuery.data.length === 0 ? (
            <ChartEmpty message="No spend in this period yet." height={280} />
          ) : (
            <CategoryBars data={spendingQuery.data} />
          )}
        </ChartCard>

        <ChartCard title="Monthly spending" subtitle="Total spend per month, last 12 months">
          {trendQuery.isLoading ? (
            <ChartSkeleton height={280} />
          ) : !trendQuery.data || trendQuery.data.length === 0 ? (
            <ChartEmpty message="No data yet." height={280} />
          ) : (
            <TrendChart data={trendQuery.data} />
          )}
        </ChartCard>

        <ChartCard
          className="lg:col-span-2"
          title="Income vs expenses"
          subtitle="Monthly totals, last 12 months"
        >
          {incomeExpenseQuery.isLoading ? (
            <ChartSkeleton height={300} />
          ) : !incomeExpenseQuery.data || incomeExpenseQuery.data.length === 0 ? (
            <ChartEmpty message="No data yet." height={300} />
          ) : (
            <IncomeExpenseChart data={incomeExpenseQuery.data} />
          )}
        </ChartCard>

        <SubscriptionsCard className="lg:col-span-2" />
      </div>
    </div>
  )
}

function KpiRow({
  incomeExpense,
  trend,
  spending,
}: {
  incomeExpense?: MonthlyIncomeExpense[]
  trend?: MonthlyAmount[]
  spending?: CategorySpending[]
}) {
  if (!incomeExpense || incomeExpense.length === 0) return null
  const current = incomeExpense[incomeExpense.length - 1]
  const previous = incomeExpense.length > 1 ? incomeExpense[incomeExpense.length - 2] : null
  const net = current.income - current.expenses
  const topCategory = spending && spending.length > 0 ? spending[0] : null

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <StatTile
        label="Spent this month"
        value={formatGBP(current.expenses)}
        delta={
          previous
            ? {
                amount: current.expenses - previous.expenses,
                formatted: formatGBPCompact(Math.abs(current.expenses - previous.expenses)),
              }
            : undefined
        }
        goodWhenDown
        sparkline={trend?.map((m) => m.total)}
      />
      <StatTile
        label="Income this month"
        value={formatGBP(current.income)}
        delta={
          previous
            ? {
                amount: current.income - previous.income,
                formatted: formatGBPCompact(Math.abs(current.income - previous.income)),
              }
            : undefined
        }
        sparkline={incomeExpense.map((m) => m.income)}
      />
      <StatTile
        label="Net this month"
        value={formatGBP(net)}
        delta={
          previous
            ? {
                amount: net - (previous.income - previous.expenses),
                formatted: formatGBPCompact(
                  Math.abs(net - (previous.income - previous.expenses)),
                ),
              }
            : undefined
        }
      />
      <StatTile
        label="Top category"
        value={topCategory ? formatGBP(topCategory.total) : '—'}
        sub={topCategory ? topCategory.category : 'No spend recorded yet'}
      />
    </div>
  )
}

// Horizontal bars, one hue: the job is comparing magnitude across categories,
// so length carries the value and colour stays quiet. Categories beyond the
// top 8 fold into a muted "Other" bar.
function CategoryBars({ data }: { data: CategorySpending[] }) {
  let bars = data
  if (data.length > MAX_CATEGORY_BARS) {
    const head = data.slice(0, MAX_CATEGORY_BARS - 1)
    const otherTotal = data
      .slice(MAX_CATEGORY_BARS - 1)
      .reduce((sum, item) => sum + item.total, 0)
    bars = [...head, { category: 'Other', total: otherTotal }]
  }
  const height = Math.max(200, bars.length * 36 + 12)

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        data={bars}
        layout="vertical"
        margin={{ top: 0, right: 56, bottom: 0, left: 0 }}
      >
        <XAxis type="number" hide />
        <YAxis
          type="category"
          dataKey="category"
          width={110}
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 12, fill: '#52514e' }}
        />
        <Tooltip cursor={{ fill: 'rgba(11,11,11,0.04)' }} content={<MoneyTooltip />} />
        <Bar dataKey="total" name="Spend" barSize={18} radius={[0, 4, 4, 0]}>
          {bars.map((entry) => (
            <Cell
              key={entry.category}
              fill={entry.category === 'Other' ? chartColors.other : chartColors.accent}
            />
          ))}
          <LabelList
            dataKey="total"
            position="right"
            formatter={(value: unknown) => formatGBPCompact(Number(value))}
            style={{ fontSize: 11, fill: '#52514e' }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

function TrendChart({ data }: { data: MonthlyAmount[] }) {
  const last = data[data.length - 1]
  const ticks = niceMoneyTicks(Math.max(...data.map((m) => m.total)))
  return (
    <ResponsiveContainer width="100%" height={280}>
      <AreaChart data={data} margin={{ top: 20, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke={chartColors.grid} />
        <XAxis
          dataKey="yearMonth"
          tickFormatter={formatMonthShort}
          tick={axisTick}
          tickLine={false}
          axisLine={{ stroke: chartColors.axisLine }}
          minTickGap={12}
        />
        <YAxis
          tickFormatter={formatGBPCompact}
          tick={axisTick}
          tickLine={false}
          axisLine={false}
          width={56}
          ticks={ticks}
          domain={[0, ticks[ticks.length - 1]]}
        />
        <Tooltip
          cursor={{ stroke: chartColors.axisLine, strokeWidth: 1 }}
          content={<MoneyTooltip labelFormatter={formatMonthLong} />}
        />
        <Area
          type="monotone"
          dataKey="total"
          name="Spending"
          stroke={chartColors.accent}
          strokeWidth={2}
          fill={chartColors.accent}
          fillOpacity={0.1}
          dot={false}
          activeDot={{ r: 4, stroke: '#ffffff', strokeWidth: 2 }}
        />
        <ReferenceDot
          x={last.yearMonth}
          y={last.total}
          r={4}
          fill={chartColors.accent}
          stroke="#ffffff"
          strokeWidth={2}
          label={{
            value: formatGBPCompact(last.total),
            position: 'top',
            fontSize: 11,
            fill: '#52514e',
          }}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}

function IncomeExpenseChart({ data }: { data: MonthlyIncomeExpense[] }) {
  const ticks = niceMoneyTicks(Math.max(...data.map((m) => Math.max(m.income, m.expenses))))
  return (
    <div>
      <div className="flex justify-end gap-4 mb-2">
        <LegendKey color={chartColors.income} label="Income" />
        <LegendKey color={chartColors.expense} label="Expenses" />
      </div>
      <ResponsiveContainer width="100%" height={300}>
      <BarChart data={data} barGap={2} barCategoryGap="24%" margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke={chartColors.grid} />
        <XAxis
          dataKey="yearMonth"
          tickFormatter={formatMonthShort}
          tick={axisTick}
          tickLine={false}
          axisLine={{ stroke: chartColors.axisLine }}
          minTickGap={12}
        />
        <YAxis
          tickFormatter={formatGBPCompact}
          tick={axisTick}
          tickLine={false}
          axisLine={false}
          width={56}
          ticks={ticks}
          domain={[0, ticks[ticks.length - 1]]}
        />
        <Tooltip
          cursor={{ fill: 'rgba(11,11,11,0.04)' }}
          content={<MoneyTooltip labelFormatter={formatMonthLong} />}
        />
        <Bar
          dataKey="income"
          name="Income"
          fill={chartColors.income}
          maxBarSize={16}
          radius={[3, 3, 0, 0]}
        />
        <Bar
          dataKey="expenses"
          name="Expenses"
          fill={chartColors.expense}
          maxBarSize={16}
          radius={[3, 3, 0, 0]}
        />
      </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function LegendKey({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-gray-600">
      <span className="inline-block h-2 w-2 rounded-sm" style={{ backgroundColor: color }} />
      {label}
    </span>
  )
}
