const API = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, init)
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

// Build a safe, human-readable error. Only the `message` field of a JSON error body is
// surfaced (truncated) — raw bodies can carry stack traces or HTML that don't belong in the UI.
async function errorMessage(response: Response): Promise<string> {
  const fallback = `Request failed (${response.status} ${response.statusText})`
  const text = await response.text().catch(() => '')
  if (!text) return fallback
  try {
    const body: unknown = JSON.parse(text)
    if (body && typeof body === 'object' && 'message' in body) {
      const message = (body as { message: unknown }).message
      if (typeof message === 'string' && message.trim()) {
        return `${fallback}: ${message.slice(0, 200)}`
      }
    }
  } catch {
    // Not JSON — deliberately not surfaced.
  }
  return fallback
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PUT',
      headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<T>(path, { method: 'POST', body: form })
  },
}
