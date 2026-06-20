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
        <h3 className="text-lg font-semibold mb-3">Transactions</h3>
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
                </tr>
              </thead>
              <tbody>
                {transactionsQuery.data.map((t) => (
                  <tr key={t.id} className="border-b border-gray-100 last:border-0">
                    <td className="px-4 py-2 text-gray-600 tabular-nums">{t.date}</td>
                    <td className="px-4 py-2">{t.description}</td>
                    <td className="px-4 py-2">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                        {t.category}
                      </span>
                    </td>
                    <td
                      className={
                        'px-4 py-2 text-right tabular-nums ' +
                        (t.amount < 0 ? 'text-red-700' : 'text-green-700')
                      }
                    >
                      {formatGBP(t.amount)}
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
