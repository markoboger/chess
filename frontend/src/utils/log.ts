type LogMethod = (...args: unknown[]) => void

function devOnly(method: LogMethod): LogMethod {
  return (...args: unknown[]) => {
    if (import.meta.env.DEV) method(...args)
  }
}

export const log = {
  debug: devOnly(console.debug.bind(console)),
  info: devOnly(console.info.bind(console)),
  warn: devOnly(console.warn.bind(console)),
  error: devOnly(console.error.bind(console)),
} as const

