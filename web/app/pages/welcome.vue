<script setup lang="ts">
definePageMeta({ layout: 'blank' })
useHead({ title: 'Welcome' })

const auth = useAuthStore()
const toasts = useToasts()
const config = useRuntimeConfig()

const busy = ref<'google' | 'guest' | 'password' | null>(null)
const showPassword = ref(false)
const registering = ref(false)
const form = ref({ name: '', email: '', password: '', password_confirmation: '' })
const errors = ref<Record<string, string>>({})

const googleReady = ref(false)
const hasGoogle = computed(() => config.public.googleClientId !== '')

async function exploreAsGuest(): Promise<void> {
  busy.value = 'guest'

  try {
    await auth.exploreAsGuest()
    await navigateTo('/app')
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    busy.value = null
  }
}

async function submitPassword(): Promise<void> {
  busy.value = 'password'
  errors.value = {}

  try {
    if (registering.value) {
      await auth.register(form.value)
    } else {
      await auth.signInWithPassword(form.value.email, form.value.password)
    }

    await navigateTo(auth.needsOnboarding ? '/onboarding' : '/app')
  } catch (error: any) {
    if (error.isValidation) {
      errors.value = Object.fromEntries(
        Object.entries(error.errors as Record<string, string[]>).map(([key, messages]) => [key, messages[0]!]),
      )
    } else {
      toasts.error(error.message)
    }
  } finally {
    busy.value = null
  }
}

/**
 * Google Identity Services hands back an ID token in the browser, which the
 * API verifies server-side before issuing its own token.
 */
function initGoogle(): void {
  if (!hasGoogle.value || googleReady.value) {
    return
  }

  const script = document.createElement('script')

  script.src = 'https://accounts.google.com/gsi/client'
  script.async = true
  script.onload = () => {
    const google = (window as any).google

    if (!google?.accounts?.id) {
      return
    }

    google.accounts.id.initialize({
      client_id: config.public.googleClientId,
      callback: async (response: { credential: string }) => {
        busy.value = 'google'

        try {
          await auth.signInWithGoogle(response.credential)
          await navigateTo(auth.needsOnboarding ? '/onboarding' : '/app')
        } catch (error: any) {
          toasts.error(error.message)
        } finally {
          busy.value = null
        }
      },
    })

    google.accounts.id.renderButton(document.getElementById('google-button'), {
      theme: window.matchMedia('(prefers-color-scheme: dark)').matches ? 'filled_black' : 'outline',
      size: 'large',
      width: 320,
      text: 'continue_with',
      shape: 'rectangular',
    })

    googleReady.value = true
  }

  document.head.appendChild(script)
}

onMounted(initGoogle)

const highlights = [
  { icon: 'camera', title: 'Scan a receipt', body: 'Pull out the merchant, date, total and line items, then split one receipt across categories.' },
  { icon: 'pie-chart', title: 'Budget by category', body: 'Set monthly limits and get told when you are getting close, not after the fact.' },
  { icon: 'arrow-right-left', title: 'Honest transfers', body: 'Moving money between your own accounts never shows up as income or spending.' },
  { icon: 'piggy-bank', title: 'Savings that stay savings', body: 'Money set aside is reported apart from spending, so saving never looks like overspending.' },
]
</script>

<template>
  <div class="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
    <!-- Pitch -->
    <section class="bg-brand relative hidden flex-col justify-between overflow-hidden p-10 text-white lg:flex">
      <div
        class="pointer-events-none absolute -top-32 -right-24 size-[28rem] rounded-full opacity-25"
        style="background: radial-gradient(circle, white 0%, transparent 65%)"
      />

      <div class="relative flex items-center gap-2.5">
        <AppLogo :size="36" />
        <span class="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
      </div>

      <div class="relative max-w-md">
        <h1 class="text-4xl leading-tight font-semibold tracking-tight">
          Know where the money went.
        </h1>
        <p class="mt-3 text-white/80">
          Record income and expenses, scan receipts, keep to a budget, and watch
          your savings build — in one place.
        </p>

        <ul class="mt-8 space-y-5">
          <li v-for="item in highlights" :key="item.title" class="flex gap-3.5">
            <span class="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg bg-white/15">
              <AppIcon :name="item.icon" :size="17" />
            </span>
            <div>
              <p class="text-sm font-medium">{{ item.title }}</p>
              <p class="mt-0.5 text-sm text-white/70">{{ item.body }}</p>
            </div>
          </li>
        </ul>
      </div>

      <p class="relative text-xs text-white/50">Your records stay yours. Receipt images are private to your account.</p>
    </section>

    <!-- Sign in -->
    <section class="flex items-center justify-center px-5 py-12 sm:px-10">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex items-center gap-2.5 lg:hidden">
          <AppLogo :size="36" />
          <span class="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
        </div>

        <h2 class="text-2xl font-semibold tracking-tight">Get started</h2>
        <p class="text-ink-soft mt-1.5 text-sm">
          Sign in to keep your records in sync, or look around with sample data first.
        </p>

        <div class="mt-7 space-y-3">
          <div v-if="hasGoogle" id="google-button" class="flex justify-center [color-scheme:light]" />

          <button
            v-else
            type="button"
            class="btn-secondary w-full justify-center"
            disabled
            title="Set NUXT_PUBLIC_GOOGLE_CLIENT_ID to enable Google sign-in"
          >
            <AppIcon name="google" :size="16" />
            Continue with Google
          </button>

          <button
            type="button"
            class="btn-secondary w-full justify-center"
            :disabled="busy !== null"
            @click="exploreAsGuest"
          >
            <AppIcon v-if="busy === 'guest'" name="refresh" :size="15" class="animate-spin" />
            <AppIcon v-else name="eye" :size="16" />
            Explore as guest
          </button>

          <p class="text-ink-faint text-center text-xs">
            The demo is filled with sample transactions, budgets, bills and receipts.
            Nothing you do there touches a real account.
          </p>
        </div>

        <div class="my-7 flex items-center gap-3">
          <span class="bg-border h-px flex-1" />
          <span class="text-ink-faint text-xs">or use an email address</span>
          <span class="bg-border h-px flex-1" />
        </div>

        <form class="space-y-3" @submit.prevent="submitPassword">
          <div v-if="registering">
            <label class="label" for="name">Name</label>
            <input id="name" v-model="form.name" type="text" class="field" autocomplete="name" required>
            <p v-if="errors.name" class="text-negative mt-1 text-xs">{{ errors.name }}</p>
          </div>

          <div>
            <label class="label" for="email">Email</label>
            <input id="email" v-model="form.email" type="email" class="field" autocomplete="email" required>
            <p v-if="errors.email" class="text-negative mt-1 text-xs">{{ errors.email }}</p>
          </div>

          <div>
            <label class="label" for="password">Password</label>
            <div class="relative">
              <input
                id="password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                class="field pr-10"
                :autocomplete="registering ? 'new-password' : 'current-password'"
                required
              >
              <button
                type="button"
                class="text-ink-faint hover:text-ink absolute inset-y-0 right-0 px-3"
                :aria-label="showPassword ? 'Hide password' : 'Show password'"
                @click="showPassword = !showPassword"
              >
                <AppIcon name="eye" :size="16" />
              </button>
            </div>
            <p v-if="errors.password" class="text-negative mt-1 text-xs">{{ errors.password }}</p>
          </div>

          <div v-if="registering">
            <label class="label" for="confirm">Confirm password</label>
            <input
              id="confirm"
              v-model="form.password_confirmation"
              type="password"
              class="field"
              autocomplete="new-password"
              required
            >
          </div>

          <button type="submit" class="btn-primary w-full justify-center" :disabled="busy !== null">
            <AppIcon v-if="busy === 'password'" name="refresh" :size="15" class="animate-spin" />
            {{ registering ? 'Create account' : 'Sign in' }}
          </button>
        </form>

        <p class="text-ink-soft mt-4 text-center text-sm">
          {{ registering ? 'Already have an account?' : 'New here?' }}
          <button type="button" class="text-brand-ink font-medium hover:underline" @click="registering = !registering">
            {{ registering ? 'Sign in' : 'Create an account' }}
          </button>
        </p>
      </div>
    </section>
  </div>
</template>
