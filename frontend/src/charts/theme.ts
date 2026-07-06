// Shared chart look. Palette slots come from a CVD-validated set (worst
// adjacent ΔE 13.3+ under simulated colour-vision deficiency; all fills
// >= 3:1 contrast on white). Chrome colours keep grid/axes recessive so
// the data is the loudest thing on the chart.
export const chartColors = {
  accent: '#2a78d6', // single-series marks (spend, savings)
  accentSoft: '#cde2fb', // meter tracks, same-ramp light step
  income: '#008300',
  expense: '#e34948',
  other: '#a3a19b', // the folded "Other" bar — muted, never a series hue
  grid: '#e8e7e2',
  axisLine: '#d6d5cf',
  deltaGood: '#006300',
  deltaBad: '#c03535',
  sparkline: '#c3c2b7',
}

// Recharts tick style — muted ink, small, tabular for clean column alignment.
export const axisTick = { fontSize: 11, fill: '#898781' }

// Round-number y-axis ticks (0 / 1,000 / 2,000 …) so the axis reads cleanly
// instead of Recharts' even splits of the data max (£650, £1.3K, …).
export function niceMoneyTicks(max: number, segments = 4): number[] {
  if (!Number.isFinite(max) || max <= 0) return [0]
  const rawStep = max / segments
  const pow = 10 ** Math.floor(Math.log10(rawStep))
  const step = ([1, 2, 2.5, 5, 10].find((c) => c * pow >= rawStep) ?? 10) * pow
  const top = Math.ceil(max / step) * step
  const ticks: number[] = []
  for (let v = 0; v <= top + step / 2; v += step) ticks.push(v)
  return ticks
}
