/**
 * Display helpers for amounts the API sends as integer minor units.
 *
 * Formatting mirrors the server: the exponent comes from the signed-in user's
 * currency, so a zero-decimal currency never shows phantom cents.
 */
export function useMoney() {
  const auth = useAuthStore()

  const exponent = computed(() => auth.user?.currency.exponent ?? 2)
  const code = computed(() => auth.currency)

  function toMajor(minor: number | null | undefined): number {
    return (minor ?? 0) / 10 ** exponent.value
  }

  function format(minor: number | null | undefined, options: { sign?: boolean; symbol?: boolean } = {}): string {
    const value = toMajor(minor)
    const formatted = new Intl.NumberFormat(auth.user?.locale ?? 'en', {
      style: options.symbol === false ? 'decimal' : 'currency',
      currency: code.value,
      minimumFractionDigits: exponent.value,
      maximumFractionDigits: exponent.value,
    }).format(Math.abs(value))

    const prefix = options.sign === true && value !== 0 ? (value > 0 ? '+' : '−') : value < 0 ? '−' : ''

    return `${prefix}${formatted}`
  }

  /** Compact form for chart axes and dense tables: ₱12.4k, ₱1.2M. */
  function compact(minor: number | null | undefined): string {
    const value = Math.abs(toMajor(minor))
    const sign = toMajor(minor) < 0 ? '−' : ''
    const symbol = auth.symbol

    if (value >= 1_000_000) {
      return `${sign}${symbol}${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`
    }

    if (value >= 1_000) {
      return `${sign}${symbol}${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}k`
    }

    return `${sign}${symbol}${value.toFixed(0)}`
  }

  /**
   * Parse what someone typed into minor units. Accepts separators and a
   * currency symbol so pasting "₱1,234.56" works.
   */
  function parse(input: string | number | null | undefined): number | null {
    if (input === null || input === undefined || input === '') {
      return null
    }

    const cleaned = String(input).replace(/[^0-9.\-]/g, '')

    if (cleaned === '' || cleaned === '-' || Number.isNaN(Number(cleaned))) {
      return null
    }

    return Math.round(Number(cleaned) * 10 ** exponent.value)
  }

  function percent(value: number | null | undefined, digits = 0): string {
    return `${((value ?? 0) * 100).toFixed(digits)}%`
  }

  return { format, compact, parse, toMajor, percent, exponent, code }
}
