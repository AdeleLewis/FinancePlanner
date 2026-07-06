import { chartColors } from './theme'

// Stat tile: label, value, optional signed delta vs last month, optional
// sparkline (de-emphasis grey line, current period marked in the accent).
export function StatTile({
  label,
  value,
  sub,
  delta,
  goodWhenDown = false,
  sparkline,
  valueColor,
}: {
  label: string
  value: string
  sub?: string
  delta?: { amount: number; formatted: string }
  goodWhenDown?: boolean
  sparkline?: number[]
  valueColor?: string
}) {
  const deltaIsGood = delta ? (goodWhenDown ? delta.amount <= 0 : delta.amount >= 0) : true
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
      <p className="text-xs font-medium text-gray-500">{label}</p>
      <div className="mt-1 flex items-end justify-between gap-3">
        <p className="text-2xl font-semibold text-gray-900" style={{ color: valueColor }}>
          {value}
        </p>
        {sparkline && sparkline.length >= 2 && <Sparkline points={sparkline} />}
      </div>
      {sub && <p className="mt-1 text-xs text-gray-500">{sub}</p>}
      {delta && (
        <p
          className="mt-1 text-xs font-medium tabular-nums"
          style={{ color: deltaIsGood ? chartColors.deltaGood : chartColors.deltaBad }}
        >
          {delta.amount >= 0 ? '↑' : '↓'} {delta.formatted}{' '}
          <span className="font-normal text-gray-500">vs last month</span>
        </p>
      )}
    </div>
  )
}

function Sparkline({ points }: { points: number[] }) {
  const width = 88
  const height = 28
  const pad = 3
  const min = Math.min(...points)
  const max = Math.max(...points)
  const range = max - min || 1
  const step = (width - pad * 2) / (points.length - 1)
  const coords = points.map((p, i) => [
    pad + i * step,
    pad + (height - pad * 2) * (1 - (p - min) / range),
  ])
  const path = coords.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
  const [lastX, lastY] = coords[coords.length - 1]
  return (
    <svg width={width} height={height} className="shrink-0" aria-hidden>
      <polyline
        points={path}
        fill="none"
        stroke={chartColors.sparkline}
        strokeWidth="1.5"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <circle cx={lastX} cy={lastY} r="2.5" fill={chartColors.accent} />
    </svg>
  )
}
