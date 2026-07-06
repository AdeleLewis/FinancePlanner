import { formatGBP } from '../util/format'

type TooltipEntry = {
  name?: string | number
  value?: string | number
  color?: string
}

// Custom Recharts tooltip: the value is the strong element and the series
// name follows it, keyed by a short stroke of the series colour.
export function MoneyTooltip({
  active,
  payload,
  label,
  labelFormatter,
}: {
  active?: boolean
  payload?: TooltipEntry[]
  label?: string | number
  labelFormatter?: (label: string) => string
}) {
  if (!active || !payload || payload.length === 0) return null
  const heading = labelFormatter ? labelFormatter(String(label ?? '')) : label
  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-md">
      {heading != null && heading !== '' && (
        <p className="text-xs font-medium text-gray-500 mb-1">{heading}</p>
      )}
      <div className="space-y-0.5">
        {payload.map((entry, i) => (
          <div key={i} className="flex items-center gap-2 text-sm">
            <span
              className="inline-block h-0.5 w-3 rounded-full"
              style={{ backgroundColor: entry.color }}
            />
            <span className="font-semibold text-gray-900 tabular-nums">
              {typeof entry.value === 'number' ? formatGBP(entry.value) : entry.value}
            </span>
            {payload.length > 1 && entry.name != null && (
              <span className="text-xs text-gray-500">{entry.name}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
