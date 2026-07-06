const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })

const gbpCompact = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
  notation: 'compact',
  maximumFractionDigits: 1,
})

const gbpWhole = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
  maximumFractionDigits: 0,
})

export function formatGBP(amount: number): string {
  return gbp.format(amount)
}

/** Short money for axis ticks and direct labels: £412, £1.2K, £4.2M. */
export function formatGBPCompact(amount: number): string {
  return Math.abs(amount) < 1000 ? gbpWhole.format(amount) : gbpCompact.format(amount)
}

/** '2026-07' → 'Jul' (with the year on January so multi-year axes stay readable). */
export function formatMonthShort(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number)
  if (!year || !month) return yearMonth
  const name = new Date(Date.UTC(year, month - 1)).toLocaleString('en-GB', {
    month: 'short',
    timeZone: 'UTC',
  })
  return month === 1 ? `${name} ${year}` : name
}

/** '2026-07' → 'July 2026'. */
export function formatMonthLong(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number)
  if (!year || !month) return yearMonth
  return new Date(Date.UTC(year, month - 1)).toLocaleString('en-GB', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
}
