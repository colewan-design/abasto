/**
 * Date and label formatting shared across screens.
 */
export function useFormat() {
  const auth = useAuthStore()
  const locale = computed(() => auth.user?.locale ?? 'en')

  function date(value: string | null | undefined, style: 'short' | 'medium' | 'long' = 'medium'): string {
    if (!value) {
      return '—'
    }

    const parsed = new Date(value.length === 10 ? `${value}T00:00:00` : value)

    return new Intl.DateTimeFormat(locale.value, {
      short: { month: 'short', day: 'numeric' },
      medium: { month: 'short', day: 'numeric', year: 'numeric' },
      long: { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' },
    }[style] as Intl.DateTimeFormatOptions).format(parsed)
  }

  /** "Today", "Yesterday", then a date — for grouping a transaction list. */
  function relativeDay(value: string): string {
    const today = new Date()
    const parsed = new Date(`${value}T00:00:00`)
    const days = Math.round((startOfDay(parsed).getTime() - startOfDay(today).getTime()) / 86_400_000)

    if (days === 0) return 'Today'
    if (days === -1) return 'Yesterday'
    if (days === 1) return 'Tomorrow'

    return date(value, parsed.getFullYear() === today.getFullYear() ? 'short' : 'medium')
  }

  function dueLabel(days: number): string {
    if (days < 0) return `${Math.abs(days)} ${plural('day', Math.abs(days))} overdue`
    if (days === 0) return 'Due today'
    if (days === 1) return 'Due tomorrow'

    return `Due in ${days} days`
  }

  function plural(word: string, count: number): string {
    return count === 1 ? word : `${word}s`
  }

  function monthLabel(yearMonth: string): string {
    const [year, month] = yearMonth.split('-').map(Number)

    return new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric' })
      .format(new Date(year!, (month ?? 1) - 1, 1))
  }

  function today(): string {
    return toInput(new Date())
  }

  function toInput(value: Date): string {
    const offset = value.getTimezoneOffset()

    return new Date(value.getTime() - offset * 60_000).toISOString().slice(0, 10)
  }

  function currentMonth(): string {
    return today().slice(0, 7)
  }

  function shiftMonth(yearMonth: string, by: number): string {
    const [year, month] = yearMonth.split('-').map(Number)
    const shifted = new Date(year!, (month ?? 1) - 1 + by, 1)

    return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`
  }

  return { date, relativeDay, dueLabel, plural, monthLabel, today, toInput, currentMonth, shiftMonth }
}

function startOfDay(value: Date): Date {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}
