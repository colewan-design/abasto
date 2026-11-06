import type { ApiErrorBody } from '~/types/api'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errors: Record<string, string[]> = {},
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** First message for a field, for rendering under an input. */
  field(name: string): string | undefined {
    return this.errors[name]?.[0]
  }

  get isValidation(): boolean {
    return this.status === 422
  }
}

/**
 * Thin typed wrapper over `$fetch` that attaches the bearer token, unwraps the
 * API's `data` envelope, and turns Laravel's validation responses into an
 * `ApiError` carrying per-field messages.
 */
export function useApi() {
  const config = useRuntimeConfig()
  const auth = useAuthStore()

  async function request<T>(
    method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE',
    path: string,
    options: { body?: unknown; query?: Record<string, any>; raw?: boolean } = {},
  ): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }

    if (auth.token !== null) {
      headers.Authorization = `Bearer ${auth.token}`
    }

    try {
      const response = await $fetch<any>(path, {
        baseURL: config.public.apiBase,
        method,
        headers,
        body: options.body as any,
        query: options.query,
      })

      if (options.raw === true || response === null || typeof response !== 'object') {
        return response as T
      }

      // Single resources arrive as { data: … }; collections and custom
      // payloads are returned whole so callers can read `meta` too.
      return ('data' in response && Object.keys(response).length === 1 ? response.data : response) as T
    } catch (error: any) {
      const status: number = error?.response?.status ?? error?.statusCode ?? 0
      const body: ApiErrorBody | undefined = error?.response?._data ?? error?.data

      if (status === 401 && auth.isAuthenticated) {
        auth.clear()
        await navigateTo('/welcome')
      }

      throw new ApiError(
        body?.message ?? error?.message ?? 'Something went wrong.',
        status,
        body?.errors ?? {},
      )
    }
  }

  return {
    get: <T>(path: string, query?: Record<string, any>) => request<T>('GET', path, { query }),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
    patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
    put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body }),
    destroy: <T>(path: string, body?: unknown) => request<T>('DELETE', path, { body }),

    /** Returns the full response including `meta`, for list endpoints. */
    list: <T>(path: string, query?: Record<string, any>) => request<T>('GET', path, { query, raw: true }),

    /** Multipart upload, used for receipt images. */
    upload: async <T>(path: string, form: FormData): Promise<T> => {
      const headers: Record<string, string> = { Accept: 'application/json' }

      if (auth.token !== null) {
        headers.Authorization = `Bearer ${auth.token}`
      }

      try {
        const response = await $fetch<any>(path, {
          baseURL: config.public.apiBase,
          method: 'POST',
          headers,
          body: form,
        })

        return (response?.data ?? response) as T
      } catch (error: any) {
        const body: ApiErrorBody | undefined = error?.response?._data ?? error?.data

        throw new ApiError(
          body?.message ?? 'Upload failed.',
          error?.response?.status ?? 0,
          body?.errors ?? {},
        )
      }
    },

    /** Absolute URL for a download endpoint, with the token as a query hint. */
    downloadUrl: (path: string, query: Record<string, any> = {}): string => {
      const url = new URL(config.public.apiBase.replace(/\/$/, '') + path)

      for (const [key, value] of Object.entries(query)) {
        if (value !== undefined && value !== null && value !== '') {
          url.searchParams.set(key, String(value))
        }
      }

      return url.toString()
    },

    /**
     * Fetch a file through the API and hand it to the browser as a download,
     * which keeps the bearer token out of the URL.
     */
    download: async (path: string, filename: string, query: Record<string, any> = {}): Promise<void> => {
      const headers: Record<string, string> = {}

      if (auth.token !== null) {
        headers.Authorization = `Bearer ${auth.token}`
      }

      const blob = await $fetch<Blob>(path, {
        baseURL: config.public.apiBase,
        headers,
        query,
        responseType: 'blob',
      })

      const href = URL.createObjectURL(blob)
      const anchor = document.createElement('a')

      anchor.href = href
      anchor.download = filename
      anchor.click()

      URL.revokeObjectURL(href)
    },
  }
}
