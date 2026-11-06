/**
 * Debounce a callback, cancelling the pending call when the component goes
 * away so a late response cannot touch a disposed component.
 */
export function useDebounceFn<T extends (...args: any[]) => any>(callback: T, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | undefined

  if (getCurrentScope()) {
    onScopeDispose(() => clearTimeout(timer))
  }

  return (...args: Parameters<T>): void => {
    clearTimeout(timer)
    timer = setTimeout(() => callback(...args), delay)
  }
}
