export interface Toast {
  id: number
  message: string
  tone: 'info' | 'success' | 'error'
}

const toasts = ref<Toast[]>([])
let nextId = 1

/**
 * Transient confirmations and errors. Kept in module scope so any component
 * can raise one without threading state through props.
 */
export function useToasts() {
  function push(message: string, tone: Toast['tone'] = 'info'): void {
    const id = nextId++

    toasts.value = [...toasts.value, { id, message, tone }]

    setTimeout(() => dismiss(id), tone === 'error' ? 6000 : 3500)
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  return {
    toasts,
    dismiss,
    notify: (message: string) => push(message, 'info'),
    success: (message: string) => push(message, 'success'),
    error: (message: string) => push(message, 'error'),
  }
}
