import { useEffect, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { formatGBP } from '../util/format'
import type { Transaction, UploadResult } from '../types'

const MAX_FILE_BYTES = 10 * 1024 * 1024

export default function Upload() {
  const queryClient = useQueryClient()

  const uploadMutation = useMutation({
    mutationFn: (file: File) => api.upload<UploadResult>('/statements', file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['insights'] })
    },
  })

  const transactionsQuery = useQuery({
    queryKey: ['transactions'],
    queryFn: () => api.get<Transaction[]>('/transactions'),
  })

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.get<string[]>('/transactions/categories'),
  })

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: { 'text/csv': ['.csv'] },
    multiple: false,
    maxSize: MAX_FILE_BYTES,
    onDrop: (files) => {
      if (files[0]) uploadMutation.mutate(files[0])
    },
  })

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-semibold mb-2">Upload Statement</h2>
        <p className="text-gray-500">Drop a CSV bank statement to import transactions.</p>
      </div>

      <div
        {...getRootProps()}
        className={
          'border-2 border-dashed rounded-lg p-12 text-center cursor-pointer transition-colors ' +
          (isDragActive
            ? 'border-blue-500 bg-blue-50'
            : 'border-gray-300 hover:border-gray-400 bg-white')
        }
      >
        <input {...getInputProps()} />
        {uploadMutation.isPending ? (
          <p className="text-gray-600">Uploading…</p>
        ) : isDragActive ? (
          <p className="text-blue-600 font-medium">Drop the CSV here</p>
        ) : (
          <div>
            <p className="text-gray-700 font-medium">Drag &amp; drop a CSV file here</p>
            <p className="text-sm text-gray-500 mt-1">or click to browse</p>
          </div>
        )}
      </div>

      {uploadMutation.isError && (
        <div className="rounded-md bg-red-50 border border-red-200 p-4">
          <p className="text-red-800 font-medium">Upload failed</p>
          <p className="text-red-700 text-sm mt-1">
            {(uploadMutation.error as Error).message}
          </p>
        </div>
      )}

      {uploadMutation.isSuccess && uploadMutation.data && (
        <div className="rounded-md bg-green-50 border border-green-200 p-4">
          <p className="text-green-800 font-medium">
            Imported {uploadMutation.data.rowCount} transactions (statement #
            {uploadMutation.data.statementId})
          </p>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-lg font-semibold">Transactions</h3>
          {transactionsQuery.data && transactionsQuery.data.length > 0 && (
            <ClearAllButton count={transactionsQuery.data.length} />
          )}
        </div>
        {transactionsQuery.isLoading && <p className="text-gray-500">Loading…</p>}
        {transactionsQuery.data && transactionsQuery.data.length === 0 && (
          <p className="text-gray-500">No transactions yet — upload a CSV to get started.</p>
        )}
        {transactionsQuery.data && transactionsQuery.data.length > 0 && (
          <div className="overflow-x-auto bg-white border border-gray-200 rounded-lg">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200 text-gray-700">
                <tr>
                  <th className="text-left px-4 py-2 font-medium">Date</th>
                  <th className="text-left px-4 py-2 font-medium">Description</th>
                  <th className="text-left px-4 py-2 font-medium">Category</th>
                  <th className="text-right px-4 py-2 font-medium">Amount</th>
                  <th className="px-4 py-2 font-medium sr-only">Actions</th>
                </tr>
              </thead>
              <tbody>
                {transactionsQuery.data.map((t) => (
                  <tr key={t.id} className="border-b border-gray-100 last:border-0">
                    <td className="px-4 py-2 text-gray-600 tabular-nums">{t.date}</td>
                    <td className="px-4 py-2">{t.description}</td>
                    <td className="px-4 py-2">
                      <CategoryCell transaction={t} categories={categoriesQuery.data ?? []} />
                    </td>
                    <td
                      className={
                        'px-4 py-2 text-right tabular-nums ' +
                        (t.amount < 0 ? 'text-red-700' : 'text-green-700')
                      }
                    >
                      {formatGBP(t.amount)}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <RemoveButton transaction={t} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

function RemoveButton({ transaction }: { transaction: Transaction }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const mutation = useMutation({
    mutationFn: () => api.delete(`/transactions/${transaction.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['insights'] })
    },
  })

  // Reset the confirm prompt if the user doesn't follow through.
  useEffect(() => {
    if (!confirming) return
    const timer = setTimeout(() => setConfirming(false), 4000)
    return () => clearTimeout(timer)
  }, [confirming])

  return (
    <button
      type="button"
      disabled={mutation.isPending}
      onClick={() => {
        if (confirming) {
          mutation.mutate()
        } else {
          setConfirming(true)
        }
      }}
      aria-label={confirming ? `Confirm removing ${transaction.description}` : `Remove ${transaction.description}`}
      className={
        'text-xs font-medium rounded px-2 py-1 disabled:opacity-50 ' +
        (confirming ? 'bg-red-600 text-white' : 'text-gray-500 hover:bg-gray-100')
      }
    >
      {confirming ? 'Confirm?' : 'Remove'}
    </button>
  )
}

function ClearAllButton({ count }: { count: number }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const mutation = useMutation({
    mutationFn: () => api.delete('/transactions'),
    onSuccess: () => {
      setConfirming(false)
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['insights'] })
    },
  })

  useEffect(() => {
    if (!confirming) return
    const timer = setTimeout(() => setConfirming(false), 4000)
    return () => clearTimeout(timer)
  }, [confirming])

  return (
    <button
      type="button"
      disabled={mutation.isPending}
      onClick={() => {
        if (confirming) {
          mutation.mutate()
        } else {
          setConfirming(true)
        }
      }}
      className={
        'text-sm font-medium rounded-md px-3 py-1.5 disabled:opacity-50 ' +
        (confirming ? 'bg-red-600 text-white' : 'text-red-700 hover:bg-red-50 border border-red-200')
      }
    >
      {mutation.isPending ? 'Clearing…' : confirming ? `Delete all ${count}? Click to confirm` : 'Clear all'}
    </button>
  )
}

function CategoryCell({ transaction, categories }: { transaction: Transaction; categories: string[] }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (category: string) =>
      api.post(`/transactions/${transaction.id}/category`, { category }),
    onSuccess: () => {
      // Re-categorising a merchant is learned server-side, so other rows + insights can change too.
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['insights'] })
    },
  })

  // Make sure the current value is always selectable, even if it isn't in the standard list.
  const options = categories.includes(transaction.category)
    ? categories
    : [transaction.category, ...categories]

  return (
    <span className="inline-flex items-center gap-1">
      <select
        value={transaction.category}
        disabled={mutation.isPending}
        onChange={(e) => mutation.mutate(e.target.value)}
        aria-label={`Category for ${transaction.description}`}
        className="text-xs font-medium bg-gray-100 text-gray-700 rounded px-2 py-1 border border-transparent hover:border-gray-300 focus:border-gray-400 focus:outline-none disabled:opacity-50"
      >
        {options.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
      {transaction.userCategorized && (
        <span className="text-gray-400" title="You set this category">
          ✓
        </span>
      )}
    </span>
  )
}
