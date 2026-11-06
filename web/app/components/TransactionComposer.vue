<script setup lang="ts">
import type { Category, CategorySuggestion, Transaction } from '~/types/api'

/**
 * Add an expense, income, or a transfer.
 *
 * Transfers post to their own endpoint because they are a pair of rows rather
 * than one, and the composer hides the category field for them — a transfer
 * has no category by design.
 */
const props = withDefaults(defineProps<{ open: boolean; mode?: 'expense' | 'income' | 'transfer' }>(), {
  mode: 'expense',
})

const emit = defineEmits<{ close: []; saved: [Transaction] }>()

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const tabs = [
  { value: 'expense', label: 'Expense', icon: 'arrow-up-right' },
  { value: 'income', label: 'Income', icon: 'arrow-down-left' },
  { value: 'transfer', label: 'Transfer', icon: 'arrow-right-left' },
] as const

const type = ref<'expense' | 'income' | 'transfer'>(props.mode)
const amount = ref('')
const accountId = ref<number | null>(null)
const toAccountId = ref<number | null>(null)
const categoryId = ref<number | null>(null)
const merchant = ref('')
const note = ref('')
const occurredOn = ref(format.today())
const selectedTags = ref<number[]>([])
const splits = ref<Array<{ category_id: number | null; amount: string }>>([])
const saving = ref(false)
const errors = ref<Record<string, string>>({})
const suggestion = ref<Category | null>(null)

const splitTotal = computed(() =>
  splits.value.reduce((total, split) => total + (money.parse(split.amount) ?? 0), 0),
)

const amountMinor = computed(() => money.parse(amount.value))

const splitMismatch = computed(() => {
  if (splits.value.length === 0 || amountMinor.value === null) {
    return null
  }

  const difference = splitTotal.value - amountMinor.value

  return difference === 0 ? null : difference
})

const canSave = computed(() => {
  if (saving.value || amountMinor.value === null || amountMinor.value <= 0 || accountId.value === null) {
    return false
  }

  if (type.value === 'transfer') {
    return toAccountId.value !== null && toAccountId.value !== accountId.value
  }

  return splitMismatch.value === null
})

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }

    await reference.load()
    type.value = props.mode
    reset()
  },
)

watch(type, () => {
  categoryId.value = null
  suggestion.value = null
  splits.value = []
})

/**
 * Ask the API what category this merchant usually means, so the common case is
 * one tap rather than a search.
 */
const askForSuggestion = useDebounceFn(async () => {
  if (merchant.value.trim().length < 3 || type.value === 'transfer') {
    return
  }

  try {
    const response = await api.post<CategorySuggestion>('/categories/suggest', {
      merchant: merchant.value,
      type: type.value,
    })

    suggestion.value = response.category

    if (response.category !== null && categoryId.value === null) {
      categoryId.value = response.category.id
    }
  } catch {
    // A suggestion is a convenience; failing to get one changes nothing.
  }
}, 400)

function reset(): void {
  amount.value = ''
  accountId.value = reference.activeAccounts[0]?.id ?? null
  toAccountId.value = null
  categoryId.value = null
  merchant.value = ''
  note.value = ''
  occurredOn.value = format.today()
  selectedTags.value = []
  splits.value = []
  errors.value = {}
  suggestion.value = null
}

function addSplit(): void {
  // The first split carries whatever is unallocated, which for a fresh split
  // is the full amount.
  const remaining = Math.max(0, (amountMinor.value ?? 0) - splitTotal.value)

  splits.value = [
    ...splits.value,
    { category_id: splits.value.length === 0 ? categoryId.value : null, amount: remaining > 0 ? money.toMajor(remaining).toFixed(money.exponent.value) : '' },
  ]
}

function removeSplit(index: number): void {
  splits.value = splits.value.filter((_, position) => position !== index)
}

function toggleTag(id: number): void {
  selectedTags.value = selectedTags.value.includes(id)
    ? selectedTags.value.filter((tag) => tag !== id)
    : [...selectedTags.value, id]
}

async function save(): Promise<void> {
  saving.value = true
  errors.value = {}

  try {
    if (type.value === 'transfer') {
      await api.post('/transfers', {
        from_account_id: accountId.value,
        to_account_id: toAccountId.value,
        amount_minor: amountMinor.value,
        occurred_on: occurredOn.value,
        note: note.value || null,
      })

      toasts.success('Transfer recorded.')
    } else {
      const transaction = await api.post<Transaction>('/transactions', {
        type: type.value,
        account_id: accountId.value,
        category_id: splits.value.length > 0 ? null : categoryId.value,
        amount_minor: amountMinor.value,
        occurred_on: occurredOn.value,
        merchant: merchant.value || null,
        note: note.value || null,
        tags: selectedTags.value,
        splits: splits.value.map((split) => ({
          category_id: split.category_id,
          amount_minor: money.parse(split.amount) ?? 0,
        })),
      })

      toasts.success(`${type.value === 'income' ? 'Income' : 'Expense'} saved.`)
      emit('saved', transaction)
    }

    await reference.refresh()
    emit('close')
  } catch (error: any) {
    if (error.isValidation) {
      errors.value = Object.fromEntries(
        Object.entries(error.errors as Record<string, string[]>).map(([key, messages]) => [key, messages[0]!]),
      )
    }

    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppModal
    :open="open"
    :title="type === 'transfer' ? 'Move money' : type === 'income' ? 'Add income' : 'Add expense'"
    @close="emit('close')"
  >
    <div class="bg-raised grid grid-cols-3 gap-1 rounded-lg p-1">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        :class="[
          'flex items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-sm font-medium transition',
          type === tab.value ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink',
        ]"
        @click="type = tab.value"
      >
        <AppIcon :name="tab.icon" :size="15" />
        {{ tab.label }}
      </button>
    </div>

    <div class="mt-4 space-y-4">
      <div>
        <label class="label" for="composer-amount">Amount</label>
        <MoneyInput id="composer-amount" v-model="amount" size="lg" autofocus />
        <p v-if="errors.amount_minor" class="text-negative mt-1.5 text-xs">{{ errors.amount_minor }}</p>
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label class="label">{{ type === 'transfer' ? 'From' : 'Account' }}</label>
          <AccountSelect v-model="accountId" :exclude="type === 'transfer' ? toAccountId : null" />
          <p v-if="errors.account_id" class="text-negative mt-1.5 text-xs">{{ errors.account_id }}</p>
        </div>

        <div v-if="type === 'transfer'">
          <label class="label">To</label>
          <AccountSelect v-model="toAccountId" :exclude="accountId" />
          <p v-if="errors.to_account_id" class="text-negative mt-1.5 text-xs">{{ errors.to_account_id }}</p>
        </div>

        <div v-else>
          <label class="label" for="composer-date">Date</label>
          <input id="composer-date" v-model="occurredOn" type="date" class="field">
        </div>
      </div>

      <div v-if="type === 'transfer'">
        <label class="label" for="transfer-date">Date</label>
        <input id="transfer-date" v-model="occurredOn" type="date" class="field">
      </div>

      <template v-if="type !== 'transfer'">
        <div>
          <label class="label" for="composer-merchant">
            {{ type === 'income' ? 'Source' : 'Merchant' }}
          </label>
          <input
            id="composer-merchant"
            v-model="merchant"
            type="text"
            class="field"
            :placeholder="type === 'income' ? 'Acme Corporation' : 'Corner Grocery'"
            autocomplete="off"
            @input="askForSuggestion"
          >
        </div>

        <div v-if="splits.length === 0">
          <label class="label">Category</label>
          <CategoryPicker v-model="categoryId" :type="type" :suggestion="suggestion" />
        </div>

        <!-- Splits -->
        <div v-else class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="label mb-0">Split across categories</span>
            <MoneyAmount
              v-if="splitMismatch !== null"
              :minor="splitMismatch"
              size="sm"
              :class="'text-negative'"
              :sign="true"
            />
          </div>

          <div v-for="(split, index) in splits" :key="index" class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <CategoryPicker v-model="split.category_id" :type="type" placeholder="Category" />
            </div>
            <div class="w-32">
              <MoneyInput v-model="split.amount" />
            </div>
            <button type="button" class="btn-ghost mt-1 p-1.5" aria-label="Remove split" @click="removeSplit(index)">
              <AppIcon name="x" :size="15" />
            </button>
          </div>

          <p v-if="errors.splits" class="text-negative text-xs">{{ errors.splits }}</p>
          <p v-else-if="splitMismatch !== null" class="text-ink-faint text-xs">
            The splits must add up to the transaction amount.
          </p>
        </div>

        <button type="button" class="btn-ghost -mt-1 px-0 text-xs" @click="addSplit">
          <AppIcon name="split" :size="14" />
          {{ splits.length === 0 ? 'Split across categories' : 'Add another split' }}
        </button>

        <div v-if="reference.tags.length > 0">
          <span class="label">Tags</span>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="tag in reference.tags"
              :key="tag.id"
              type="button"
              :class="[
                'chip border transition',
                selectedTags.includes(tag.id)
                  ? 'border-brand bg-brand-soft text-brand-ink'
                  : 'border-border text-ink-soft',
              ]"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </button>
          </div>
        </div>
      </template>

      <div>
        <label class="label" for="composer-note">Note</label>
        <textarea id="composer-note" v-model="note" rows="2" class="field resize-none" placeholder="Optional" />
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn-ghost" @click="emit('close')">Cancel</button>
        <button type="button" class="btn-primary" :disabled="!canSave" @click="save">
          <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
          Save
        </button>
      </div>
    </template>
  </AppModal>
</template>
