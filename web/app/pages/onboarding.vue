<script setup lang="ts">
import type { OnboardingOptions } from '~/types/api'

definePageMeta({ layout: 'blank' })
useHead({ title: 'Set up' })

const api = useApi()
const auth = useAuthStore()
const money = useMoney()
const toasts = useToasts()
const reference = useReferenceStore()

const options = ref<OnboardingOptions | null>(null)
const step = ref(0)
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const currency = ref('PHP')
const timezone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC')

interface AccountDraft {
  name: string
  type: string
  opening: string
  credit_limit: string
}

const accounts = ref<AccountDraft[]>([
  { name: 'Cash', type: 'cash', opening: '', credit_limit: '' },
])

const selectedCategories = ref<string[]>([])
const overallBudget = ref('')
const categoryLimits = ref<Record<string, string>>({})

const steps = [
  { title: 'Currency', hint: 'Everything is recorded and reported in this currency.' },
  { title: 'Accounts', hint: 'Where your money sits today, and how much is in each.' },
  { title: 'Categories', hint: 'Pick the ones you will use. You can add more later.' },
  { title: 'Budget', hint: 'Optional monthly limits. Leave blank to set them up later.' },
]

const selectedCurrency = computed(() =>
  options.value?.currencies.find((entry) => entry.code === currency.value),
)

const budgetableCategories = computed(() =>
  selectedCategories.value.filter((name) => {
    const groups = options.value?.catalog.find((entry) => entry.type === 'expense')?.groups ?? []

    return groups.some((group) => group.categories.some((category) => category.name === name))
  }),
)

const canContinue = computed(() => {
  if (step.value === 0) {
    return currency.value !== ''
  }

  if (step.value === 1) {
    return accounts.value.length > 0 && accounts.value.every((account) => account.name.trim() !== '')
  }

  return true
})

onMounted(async () => {
  options.value = await api.get<OnboardingOptions>('/onboarding/options')
  currency.value = options.value.detected_currency || 'PHP'
  selectedCategories.value = [...options.value.recommended_categories]
})

function addAccount(): void {
  accounts.value = [...accounts.value, { name: '', type: 'bank', opening: '', credit_limit: '' }]
}

function removeAccount(index: number): void {
  accounts.value = accounts.value.filter((_, position) => position !== index)
}

function toggleCategory(name: string): void {
  selectedCategories.value = selectedCategories.value.includes(name)
    ? selectedCategories.value.filter((entry) => entry !== name)
    : [...selectedCategories.value, name]
}

function isLiability(type: string): boolean {
  return options.value?.account_types.find((entry) => entry.value === type)?.is_liability ?? false
}

async function finish(): Promise<void> {
  saving.value = true
  errors.value = {}

  const limits = Object.entries(categoryLimits.value)
    .map(([name, value]) => ({ category_name: name, amount_minor: money.parse(value) }))
    .filter((line): line is { category_name: string; amount_minor: number } => line.amount_minor !== null && line.amount_minor > 0)

  try {
    await api.post('/onboarding/complete', {
      currency_code: currency.value,
      timezone,
      accounts: accounts.value.map((account) => ({
        name: account.name.trim(),
        type: account.type,
        opening_balance_minor: money.parse(account.opening) ?? 0,
        credit_limit_minor: isLiability(account.type) ? money.parse(account.credit_limit) : null,
      })),
      categories: selectedCategories.value,
      budget: money.parse(overallBudget.value) !== null || limits.length > 0
        ? { overall_amount_minor: money.parse(overallBudget.value), categories: limits }
        : null,
    })

    await auth.refreshUser()
    await reference.refresh()

    toasts.success("You're all set.")
    await navigateTo('/app')
  } catch (error: any) {
    if (error.isValidation) {
      errors.value = Object.fromEntries(
        Object.entries(error.errors as Record<string, string[]>).map(([key, messages]) => [key, messages[0]!]),
      )

      // Send the user back to the step that owns the problem.
      if (Object.keys(errors.value).some((key) => key.startsWith('accounts'))) {
        step.value = 1
      } else if (errors.value.currency_code) {
        step.value = 0
      }
    }

    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mx-auto flex min-h-dvh max-w-2xl flex-col px-5 py-8 sm:px-6">
    <header class="flex items-center gap-2.5">
      <span class="bg-brand grid size-8 place-items-center rounded-lg text-white">
        <AppIcon name="wallet" :size="17" />
      </span>
      <span class="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
    </header>

    <!-- Progress -->
    <ol class="mt-8 flex items-center gap-2">
      <li v-for="(entry, index) in steps" :key="entry.title" class="flex flex-1 items-center gap-2">
        <span
          :class="[
            'grid size-6 shrink-0 place-items-center rounded-full text-[11px] font-semibold transition',
            index < step ? 'bg-brand text-white' : index === step ? 'bg-brand-soft text-brand-ink ring-brand ring-2' : 'bg-raised text-ink-faint',
          ]"
        >
          <AppIcon v-if="index < step" name="check" :size="13" />
          <template v-else>{{ index + 1 }}</template>
        </span>
        <span :class="['hidden text-xs font-medium sm:block', index === step ? 'text-ink' : 'text-ink-faint']">
          {{ entry.title }}
        </span>
        <span v-if="index < steps.length - 1" class="bg-border h-px flex-1" />
      </li>
    </ol>

    <div v-if="options === null" class="mt-10 space-y-3">
      <div class="skeleton h-8 w-48" />
      <div class="skeleton h-32" />
    </div>

    <div v-else class="mt-8 flex-1">
      <h1 class="text-2xl font-semibold tracking-tight">{{ steps[step]!.title }}</h1>
      <p class="text-ink-soft mt-1.5 text-sm">{{ steps[step]!.hint }}</p>

      <!-- Currency -->
      <div v-if="step === 0" class="mt-6 grid gap-2 sm:grid-cols-2">
        <button
          v-for="entry in options.currencies"
          :key="entry.code"
          type="button"
          :class="[
            'flex items-center gap-3 rounded-lg border px-3.5 py-3 text-left transition',
            currency === entry.code ? 'border-brand bg-brand-soft' : 'border-border hover:border-border-strong',
          ]"
          @click="currency = entry.code"
        >
          <span class="bg-raised tnum grid size-9 shrink-0 place-items-center rounded-lg text-sm font-semibold">
            {{ entry.symbol }}
          </span>
          <span class="min-w-0">
            <span class="block text-sm font-medium">{{ entry.code }}</span>
            <span class="text-ink-faint block truncate text-xs">{{ entry.name }}</span>
          </span>
        </button>
      </div>

      <!-- Accounts -->
      <div v-else-if="step === 1" class="mt-6 space-y-3">
        <div v-for="(account, index) in accounts" :key="index" class="card p-3.5">
          <div class="flex items-start gap-2">
            <div class="grid flex-1 gap-3 sm:grid-cols-2">
              <div>
                <label class="label">Name</label>
                <input v-model="account.name" type="text" class="field" placeholder="e.g. Savings">
                <p v-if="errors[`accounts.${index}.name`]" class="text-negative mt-1 text-xs">
                  {{ errors[`accounts.${index}.name`] }}
                </p>
              </div>

              <div>
                <label class="label">Type</label>
                <select v-model="account.type" class="field">
                  <option v-for="entry in options.account_types" :key="entry.value" :value="entry.value">
                    {{ entry.label }}
                  </option>
                </select>
              </div>

              <div>
                <label class="label">{{ isLiability(account.type) ? 'Amount owed' : 'Current balance' }}</label>
                <MoneyInput v-model="account.opening" />
              </div>

              <div v-if="isLiability(account.type)">
                <label class="label">Credit limit</label>
                <MoneyInput v-model="account.credit_limit" />
              </div>
            </div>

            <button
              v-if="accounts.length > 1"
              type="button"
              class="btn-ghost mt-6 p-1.5"
              aria-label="Remove account"
              @click="removeAccount(index)"
            >
              <AppIcon name="trash" :size="15" />
            </button>
          </div>
        </div>

        <button type="button" class="btn-secondary w-full justify-center" @click="addAccount">
          <AppIcon name="plus" :size="15" />
          Add another account
        </button>

        <p class="text-ink-faint text-xs">
          A credit card's balance is money owed, so enter what you owe — it is tracked
          as a liability rather than available cash.
        </p>
      </div>

      <!-- Categories -->
      <div v-else-if="step === 2" class="mt-6 space-y-6">
        <div v-for="section in options.catalog" :key="section.type">
          <p class="text-ink-soft mb-2 text-xs font-medium tracking-wide uppercase">
            {{ section.type === 'income' ? 'Income' : 'Spending' }}
          </p>

          <div class="space-y-3">
            <div v-for="group in section.groups" :key="group.name">
              <p class="text-ink-faint mb-1.5 text-xs">{{ group.name }}</p>
              <div class="flex flex-wrap gap-1.5">
                <button
                  v-for="category in group.categories"
                  :key="category.name"
                  type="button"
                  :class="[
                    'chip border transition',
                    selectedCategories.includes(category.name)
                      ? 'border-brand bg-brand-soft text-brand-ink'
                      : 'border-border text-ink-soft hover:border-border-strong',
                  ]"
                  @click="toggleCategory(category.name)"
                >
                  <AppIcon v-if="selectedCategories.includes(category.name)" name="check" :size="12" />
                  {{ category.name }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <p class="text-ink-faint text-xs">
          {{ selectedCategories.length }} selected. Suggestions for receipts and
          merchants are built from these, and you can rename or add more at any time.
        </p>
      </div>

      <!-- Budget -->
      <div v-else class="mt-6 space-y-5">
        <div>
          <label class="label">Overall monthly budget</label>
          <MoneyInput v-model="overallBudget" size="lg" />
          <p class="text-ink-faint mt-1.5 text-xs">Optional. This is the ceiling for all spending in a month.</p>
        </div>

        <div v-if="budgetableCategories.length > 0">
          <span class="label">Category limits</span>
          <div class="space-y-2">
            <div v-for="name in budgetableCategories" :key="name" class="flex items-center gap-3">
              <span class="min-w-0 flex-1 truncate text-sm">{{ name }}</span>
              <div class="w-36">
                <MoneyInput v-model="categoryLimits[name]" />
              </div>
            </div>
          </div>
        </div>

        <div class="bg-brand-soft text-brand-ink rounded-lg px-3.5 py-3 text-sm">
          You can skip this. Once you have a month of history the app will suggest
          limits from what you actually spent.
        </div>
      </div>
    </div>

    <!-- Controls -->
    <footer class="mt-8 flex items-center justify-between gap-3">
      <button v-if="step > 0" type="button" class="btn-ghost" @click="step -= 1">
        <AppIcon name="chevron-left" :size="16" />
        Back
      </button>
      <span v-else />

      <div class="flex items-center gap-2">
        <span v-if="selectedCurrency" class="text-ink-faint hidden text-xs sm:block">
          {{ selectedCurrency.code }} · {{ selectedCurrency.symbol }}
        </span>

        <button
          v-if="step < steps.length - 1"
          type="button"
          class="btn-primary"
          :disabled="!canContinue"
          @click="step += 1"
        >
          Continue
          <AppIcon name="chevron-right" :size="16" />
        </button>

        <button v-else type="button" class="btn-primary" :disabled="saving" @click="finish">
          <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
          Finish setup
        </button>
      </div>
    </footer>
  </div>
</template>
