import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { formatGBP } from '../util/format'
import type { Recommendation, SavingsSnapshot } from '../types'

const SnapshotSchema = z.object({
  amount: z.number().nonnegative(),
  accountName: z.string().optional(),
})
type SnapshotForm = z.infer<typeof SnapshotSchema>

const RecommendSchema = z.object({
  currentSavings: z.number().nonnegative(),
  targetAmount: z.number().positive(),
  targetDate: z.string().min(1),
})
type RecommendForm = z.infer<typeof RecommendSchema>

export default function Savings() {
  const queryClient = useQueryClient()
  const [recommendation, setRecommendation] = useState<Recommendation | null>(null)

  const snapshotsQuery = useQuery({
    queryKey: ['savings', 'snapshots'],
    queryFn: () => api.get<SavingsSnapshot[]>('/savings/snapshots'),
  })

  const sortedSnapshots = (snapshotsQuery.data ?? [])
    .slice()
    .sort((a, b) => new Date(b.recordedAt).getTime() - new Date(a.recordedAt).getTime())
  const latestSnapshot = sortedSnapshots[0]

  const addSnapshot = useMutation({
    mutationFn: (data: SnapshotForm) =>
      api.post<SavingsSnapshot>('/savings/snapshots', {
        amount: data.amount,
        accountName: data.accountName || null,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['savings', 'snapshots'] }),
  })

  const deleteSnapshot = useMutation({
    mutationFn: (id: number) => api.delete<void>(`/savings/snapshots/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['savings', 'snapshots'] }),
  })

  const recommend = useMutation({
    mutationFn: (data: RecommendForm) =>
      api.post<Recommendation>('/savings/recommend', {
        currentSavings: data.currentSavings,
        targetAmount: data.targetAmount,
        targetDate: data.targetDate,
      }),
    onSuccess: (data) => setRecommendation(data),
  })

  const snapshotForm = useForm<SnapshotForm>({
    resolver: zodResolver(SnapshotSchema),
    defaultValues: { amount: 0, accountName: '' },
  })

  const recommendForm = useForm<RecommendForm>({
    resolver: zodResolver(RecommendSchema),
    defaultValues: { currentSavings: 0, targetAmount: 0, targetDate: '' },
  })

  useEffect(() => {
    if (latestSnapshot) {
      recommendForm.setValue('currentSavings', latestSnapshot.amount)
    }
  }, [latestSnapshot?.id, latestSnapshot?.amount, recommendForm])

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-semibold">Savings</h2>

      <section className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <h3 className="text-lg font-semibold">Current savings</h3>
        <form
          onSubmit={snapshotForm.handleSubmit((data) => {
            addSnapshot.mutate(data, { onSuccess: () => snapshotForm.reset() })
          })}
          className="flex flex-wrap gap-3 items-start"
        >
          <div>
            <label className="block text-sm text-gray-600 mb-1">Amount (£)</label>
            <input
              type="number"
              step="0.01"
              {...snapshotForm.register('amount', { valueAsNumber: true })}
              className="border border-gray-300 rounded-md px-3 py-2 w-32"
            />
            {snapshotForm.formState.errors.amount && (
              <p className="text-xs text-red-600 mt-1">
                {snapshotForm.formState.errors.amount.message}
              </p>
            )}
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Account (optional)</label>
            <input
              type="text"
              {...snapshotForm.register('accountName')}
              className="border border-gray-300 rounded-md px-3 py-2 w-48"
              placeholder="e.g. Vanguard ISA"
            />
          </div>
          <div className="self-end">
            <button
              type="submit"
              disabled={addSnapshot.isPending}
              className="bg-gray-900 text-white px-4 py-2 rounded-md text-sm font-medium disabled:opacity-50"
            >
              {addSnapshot.isPending ? 'Saving…' : 'Add snapshot'}
            </button>
          </div>
        </form>

        {sortedSnapshots.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-gray-700 border-b border-gray-200">
                <tr>
                  <th className="text-left py-2 font-medium">Recorded</th>
                  <th className="text-left py-2 font-medium">Account</th>
                  <th className="text-right py-2 font-medium">Amount</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {sortedSnapshots.map((s) => (
                  <tr key={s.id} className="border-b border-gray-100 last:border-0">
                    <td className="py-2 text-gray-600 tabular-nums">
                      {new Date(s.recordedAt).toLocaleString()}
                    </td>
                    <td className="py-2">{s.accountName ?? '—'}</td>
                    <td className="py-2 text-right tabular-nums">{formatGBP(s.amount)}</td>
                    <td className="py-2 text-right">
                      <button
                        type="button"
                        onClick={() => deleteSnapshot.mutate(s.id)}
                        className="text-xs text-red-700 hover:underline"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <h3 className="text-lg font-semibold">Savings goal recommendation</h3>
        <form
          onSubmit={recommendForm.handleSubmit((data) => recommend.mutate(data))}
          className="grid grid-cols-1 sm:grid-cols-3 gap-3"
        >
          <div>
            <label className="block text-sm text-gray-600 mb-1">Current savings (£)</label>
            <input
              type="number"
              step="0.01"
              {...recommendForm.register('currentSavings', { valueAsNumber: true })}
              className="border border-gray-300 rounded-md px-3 py-2 w-full"
            />
            {recommendForm.formState.errors.currentSavings && (
              <p className="text-xs text-red-600 mt-1">
                {recommendForm.formState.errors.currentSavings.message}
              </p>
            )}
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Target amount (£)</label>
            <input
              type="number"
              step="0.01"
              {...recommendForm.register('targetAmount', { valueAsNumber: true })}
              className="border border-gray-300 rounded-md px-3 py-2 w-full"
            />
            {recommendForm.formState.errors.targetAmount && (
              <p className="text-xs text-red-600 mt-1">
                {recommendForm.formState.errors.targetAmount.message}
              </p>
            )}
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Target date</label>
            <input
              type="date"
              {...recommendForm.register('targetDate')}
              className="border border-gray-300 rounded-md px-3 py-2 w-full"
            />
            {recommendForm.formState.errors.targetDate && (
              <p className="text-xs text-red-600 mt-1">
                {recommendForm.formState.errors.targetDate.message}
              </p>
            )}
          </div>
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={recommend.isPending}
              className="bg-gray-900 text-white px-4 py-2 rounded-md text-sm font-medium disabled:opacity-50"
            >
              {recommend.isPending ? 'Calculating…' : 'Get recommendation'}
            </button>
          </div>
        </form>

        {recommend.isError && (
          <div className="rounded-md bg-red-50 border border-red-200 p-3">
            <p className="text-red-700 text-sm">{(recommend.error as Error).message}</p>
          </div>
        )}

        {recommendation && (
          <div
            className={
              'rounded-md p-4 ' +
              (recommendation.onTrack
                ? 'bg-green-50 border border-green-200'
                : 'bg-amber-50 border border-amber-200')
            }
          >
            <div className="flex items-center gap-2 mb-3">
              <span
                className={
                  'text-sm font-semibold ' +
                  (recommendation.onTrack ? 'text-green-800' : 'text-amber-800')
                }
              >
                {recommendation.onTrack ? '✓ On track' : '⚠ Stretch goal'}
              </span>
              <span className="text-xs text-gray-600">
                {recommendation.monthsRemaining} months remaining
              </span>
            </div>
            <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <Stat label="Gap to close" value={formatGBP(recommendation.gap)} />
              <Stat label="Monthly needed" value={formatGBP(recommendation.monthlyNeeded)} />
              <Stat
                label="Avg monthly disposable"
                value={formatGBP(recommendation.avgMonthlyDisposable)}
              />
              <Stat
                label="Headroom"
                value={formatGBP(
                  recommendation.avgMonthlyDisposable - recommendation.monthlyNeeded,
                )}
              />
            </dl>
            {!recommendation.onTrack && recommendation.suggestedCategoriesToCut.length > 0 && (
              <p className="text-sm text-amber-900 mt-3">
                Consider cutting back on:{' '}
                <strong>{recommendation.suggestedCategoriesToCut.join(', ')}</strong>
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-gray-600">{label}</dt>
      <dd className="font-semibold text-gray-900 tabular-nums">{value}</dd>
    </div>
  )
}
