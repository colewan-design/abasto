import { defineStore } from 'pinia'
import type { AuthResponse, User } from '~/types/api'

const TOKEN_KEY = 'budget-tracker.token'

/**
 * Holds the API token and the signed-in user.
 *
 * The token lives in localStorage so a reload does not sign the user out, and
 * is read back lazily because this store is also constructed during SSR-less
 * hydration before `window` is touched elsewhere.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null as string | null,
    user: null as User | null,
    ready: false,
  }),

  getters: {
    isAuthenticated: (state): boolean => state.token !== null,
    isGuest: (state): boolean => state.user?.is_guest === true,
    needsOnboarding: (state): boolean => state.user !== null && !state.user.onboarding_completed,
    currency: (state): string => state.user?.currency.code ?? 'PHP',
    symbol: (state): string => state.user?.currency.symbol ?? '₱',
  },

  actions: {
    /**
     * Restore the session on first load and confirm the token is still valid.
     */
    async restore(): Promise<void> {
      if (this.ready) {
        return
      }

      this.token = import.meta.client ? localStorage.getItem(TOKEN_KEY) : null

      if (this.token !== null) {
        try {
          this.user = await useApi().get<User>('/me')
        } catch {
          this.clear()
        }
      }

      this.ready = true
    },

    accept(auth: AuthResponse): void {
      this.token = auth.token
      this.user = auth.user

      if (import.meta.client) {
        localStorage.setItem(TOKEN_KEY, auth.token)
      }
    },

    setUser(user: User): void {
      this.user = user
    },

    async signInWithPassword(email: string, password: string): Promise<void> {
      this.accept(await useApi().post<AuthResponse>('/auth/login', { email, password, device_name: deviceName() }))
    },

    async register(payload: { name: string; email: string; password: string; password_confirmation: string }): Promise<void> {
      this.accept(await useApi().post<AuthResponse>('/auth/register', { ...payload, device_name: deviceName() }))
    },

    async signInWithGoogle(idToken: string): Promise<void> {
      this.accept(await useApi().post<AuthResponse>('/auth/google', { id_token: idToken, device_name: deviceName() }))
    },

    async exploreAsGuest(): Promise<void> {
      this.accept(await useApi().post<AuthResponse>('/auth/guest', { device_name: deviceName() }))
    },

    async signOut(): Promise<void> {
      try {
        await useApi().post('/auth/logout')
      } catch {
        // The token is being discarded either way.
      }

      this.clear()
      await navigateTo('/welcome')
    },

    clear(): void {
      this.token = null
      this.user = null

      if (import.meta.client) {
        localStorage.removeItem(TOKEN_KEY)
      }
    },

    async refreshUser(): Promise<void> {
      if (this.token === null) {
        return
      }

      this.user = await useApi().get<User>('/me')
    },
  },
})

function deviceName(): string {
  return import.meta.client ? `Web · ${navigator.platform || 'browser'}` : 'Web'
}
