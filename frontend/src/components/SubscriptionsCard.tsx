import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { ChartCard, ChartEmpty } from '../charts/ChartCard'
import { formatGBP } from '../util/format'
import type { Subscription, SubscriptionDecision } from '../types'

// Recurring monthly payments detected from transaction history, each with a
// keep/cancel verdict so the user can plan what to cut.
export function SubscriptionsCard({ className }: { className?: string }) {
  const queryClient = useQueryClient()

  const subscriptionsQuery = useQuery({
    queryKey: ['subscriptions'],
    queryFn: () => api.get<Subscription[]>('/subscriptions'),
  })

  const decide = useMutation({
    mutationFn: (input: { merchantKey: string; decision: SubscriptionDecision }) =>
      api.put<void>('/subscriptions/decision', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subscriptions'] }),
  })

  const subscriptions = subscriptionsQuery.data ?? []
  const monthlyTotal = subscriptions.reduce((sum, s) => sum + s.monthlyAmount, 0)
  const cancelTotal = subscriptions
    .filter((s) => s.decision === 'CANCEL')
    .reduce((sum, s) => sum + s.monthlyAmount, 0)

  return (
    <ChartCard
      className={className}
      title="Subscriptions"
      subtitle="Recurring monthly payments spotted in your transactions — mark what to keep or cancel"
    >
      {subscriptionsQuery.isLoading ? (
        <ListSkeleton />
      ) : subscriptions.length === 0 ? (
        <ChartEmpty
          message="No recurring payments detected yet — they show up once the same charge lands a few months running."
          height={160}
        />
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1">
            <p className="text-sm text-gray-600">
              {subscriptions.length} subscription{subscriptions.length === 1 ? '' : 's'} ·{' '}
              <span className="font-semibold text-gray-900 tabular-nums">
                {formatGBP(monthlyTotal)}
              </span>{' '}
              a month
            </p>
            {cancelTotal > 0 && (
              <p className="text-sm font-medium text-emerald-700">
                Cancelling saves {formatGBP(cancelTotal)} a month
              </p>
            )}
          </div>

          <ul className="divide-y divide-gray-100 border-t border-gray-100">
            {subscriptions.map((s) => (
              <SubscriptionRow
                key={s.merchantKey}
                subscription={s}
                pending={decide.isPending && decide.variables?.merchantKey === s.merchantKey}
                onDecide={(decision) => decide.mutate({ merchantKey: s.merchantKey, decision })}
              />
            ))}
          </ul>
        </div>
      )}
    </ChartCard>
  )
}

function SubscriptionRow({
  subscription,
  pending,
  onDecide,
}: {
  subscription: Subscription
  pending: boolean
  onDecide: (decision: SubscriptionDecision) => void
}) {
  const cancelled = subscription.decision === 'CANCEL'
  return (
    <li className="flex items-center justify-between gap-4 py-3">
      <div className="min-w-0">
        <p className={'text-sm font-medium truncate ' + (cancelled ? 'text-gray-400' : 'text-gray-900')}>
          {subscription.name}
        </p>
        <p className="text-xs text-gray-500 mt-0.5">
          {subscription.category} · next expected {formatShortDate(subscription.nextExpectedOn)} ·
          charged {subscription.timesCharged}×
        </p>
      </div>
      <div className="flex items-center gap-4 shrink-0">
        <p
          className={
            'text-sm font-semibold tabular-nums ' +
            (cancelled ? 'text-gray-400 line-through' : 'text-gray-900')
          }
        >
          {formatGBP(subscription.monthlyAmount)}
          <span className="text-xs font-normal text-gray-500">/mo</span>
        </p>
        <div className="inline-flex rounded-lg border border-gray-200 p-0.5" role="group">
          <DecisionButton
            label="Keep"
            active={subscription.decision === 'KEEP'}
            activeClass="bg-emerald-600 text-white"
            disabled={pending}
            onClick={() => onDecide(subscription.decision === 'KEEP' ? 'UNDECIDED' : 'KEEP')}
          />
          <DecisionButton
            label="Cancel"
            active={cancelled}
            activeClass="bg-red-600 text-white"
            disabled={pending}
            onClick={() => onDecide(cancelled ? 'UNDECIDED' : 'CANCEL')}
          />
        </div>
      </div>
    </li>
  )
}

function DecisionButton({
  label,
  active,
  activeClass,
  disabled,
  onClick,
}: {
  label: string
  active: boolean
  activeClass: string
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      disabled={disabled}
      onClick={onClick}
      className={
        'px-2.5 py-1 rounded-md text-xs font-medium transition-colors disabled:opacity-50 ' +
        (active ? activeClass : 'text-gray-600 hover:bg-gray-100')
      }
    >
      {label}
    </button>
  )
}

function ListSkeleton() {
  return (
    <div className="animate-pulse space-y-3" aria-hidden>
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center justify-between gap-4">
          <div className="space-y-2 flex-1">
            <div className="h-3.5 w-40 rounded bg-gray-200" />
            <div className="h-3 w-64 rounded bg-gray-100" />
          </div>
          <div className="h-7 w-32 rounded-lg bg-gray-100" />
        </div>
      ))}
    </div>
  )
}

function formatShortDate(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}
