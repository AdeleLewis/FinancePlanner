export function ChartCard({
  title,
  subtitle,
  className,
  children,
}: {
  title: string
  subtitle?: string
  className?: string
  children: React.ReactNode
}) {
  return (
    <div
      className={
        'bg-white border border-gray-200 rounded-xl p-5 shadow-sm ' + (className ?? '')
      }
    >
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
        {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
      </div>
      {children}
    </div>
  )
}

export function ChartSkeleton({ height = 300 }: { height?: number }) {
  return (
    <div
      className="animate-pulse rounded-lg bg-gray-100 flex items-end gap-2 p-4"
      style={{ height }}
      aria-hidden
    >
      {[45, 70, 55, 85, 60, 75, 50].map((h, i) => (
        <div key={i} className="flex-1 rounded-t bg-gray-200" style={{ height: `${h}%` }} />
      ))}
    </div>
  )
}

export function ChartEmpty({ message, height = 300 }: { message: string; height?: number }) {
  return (
    <div
      className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-gray-200 text-center"
      style={{ height }}
    >
      <svg
        className="h-8 w-8 text-gray-300"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        aria-hidden
      >
        <path d="M4 19V5M4 19h16" />
        <path d="M8 15l3-4 3 2 4-6" />
      </svg>
      <p className="text-sm text-gray-500">{message}</p>
    </div>
  )
}
