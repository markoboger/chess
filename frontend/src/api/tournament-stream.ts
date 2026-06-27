/** Read newline-delimited JSON from a tournament server stream (requires Bearer token). */
export function tournamentBaseUrl(): string {
  return (import.meta.env.VITE_TOURNAMENT_API_URL || 'http://141.37.123.132:8086').replace(/\/$/, '')
}

export type NdjsonHandler = (line: Record<string, unknown>) => void

/**
 * Opens a long-lived GET stream and invokes `onLine` for each JSON object.
 * Resolves when the stream closes or aborts; rejects on HTTP errors.
 */
export async function streamNdjson(
  path: string,
  options: {
    token?: string | null
    signal?: AbortSignal
    onLine: NdjsonHandler
  }
): Promise<void> {
  const headers: Record<string, string> = { Accept: 'application/x-ndjson' }
  if (options.token) headers.Authorization = `Bearer ${options.token}`

  const resp = await fetch(`${tournamentBaseUrl()}${path}`, { headers, signal: options.signal })
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`Stream ${path} failed: HTTP ${resp.status}${text ? ` — ${text}` : ''}`)
  }
  if (!resp.body) throw new Error(`Stream ${path}: empty body`)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const raw of lines) {
        const line = raw.trim()
        if (!line) continue
        try {
          options.onLine(JSON.parse(line) as Record<string, unknown>)
        } catch {
          /* skip malformed lines */
        }
      }
    }
    const tail = buffer.trim()
    if (tail) {
      try {
        options.onLine(JSON.parse(tail) as Record<string, unknown>)
      } catch {
        /* ignore */
      }
    }
  } finally {
    reader.releaseLock()
  }
}
