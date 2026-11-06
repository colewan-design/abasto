<script setup lang="ts">
import type { Paginated, Transaction, TransactionFilters } from '~/types/api'

useHead({ title: 'Transactions' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()
const route = useRoute()

interface ListResponse extends Paginated<Transaction> {
  meta: {
    current_page: number
    last_page: number
    total: number
    totals: {
      count: number
      income_minor: number
      expense_minor: number
      net_minor: number
      formatted: { income: string; expense: string; net: string }
    }
  }
}

const transactions = ref<Transaction[]>([])
const meta = ref<ListResponse['meta'] | null>(null)
const pending = ref(true)
const page = ref(1)
const selected = ref<Transaction | null>(null)
const filtersOpen = ref(false)

const filters = ref<TransactionFilters>({
  q: (route.query.q as string) ?? '',
  type: (route.query.type as any) ?? 'all',
  account_id: [],
  category_id: [],
  from: (route.query.from as string) ?? '',
  to: (route.query.to as string) ?? '',
  min_amount: '',
  max_amount: '',
  uncategorized: false,
  has_receipt: undefined,
  sort: 'occurred_on',
  direction: 'desc',
})

const activeFilterCount = computed(() => {
  const value = filters.value
  let count = 0

  if (value.type && value.type !== 'all') count++
  if (value.account_id?.length) count++
  if (value.category_id?.length) count++
  if (value.from || value.to) count++
  if (value.min_amount || value.max_amount) count++
  if (value.uncategorized) count++
  if (value.has_receipt !== undefined) count++

  return count
})

function queryFor(target: number): Record<string, any> {
  const value = filters.value

  return {
    page: target,
    per_page: 50,
    q: value.q || undefined,
    type: value.type !== 'all' ? value.type : undefined,
    account_id: value.account_id?.length ? value.account_id.join(',') : undefined,
    category_id: value.category_id?.length ? value.category_id.join(',') : undefined,
    from: value.from || undefined,
    to: value.to || undefined,
    min_amount: value.min_amount || undefined,
    max_amount: value.max_amount || undefined,
    uncategorized: value.uncategorized ? 1 : undefined,
    has_receipt: value.has_receipt === undefined ? undefined : value.has_receipt ? 1 : 0,
    sort: value.sort,
    direction: value.direction,
  }
}

async function load(append = false): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<ListResponse>('/transactions', queryFor(page.value))

    transactions.value = append ? [...transactions.value, ...response.data] : response.data
    meta.value = response.meta
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

const search = useDebounceFn(() => {
  page.value = 1
  load()
}, 350)

function apply(): void {
  page.value = 1
  filtersOpen.value = false
  load()
}

function reset(): void {
  filters.value = {
    q: '',
    type: 'all',
    account_id: [],
    category_id: [],
    from: '',
    to: '',
    min_amount: '',
    max_amount: '',
    uncategorized: false,
    has_receipt: undefined,
    sort: 'occurred_on',
    direction: 'desc',
  }

  apply()
}

async function loadMore(): Promise<void> {
  page.value += 1
  await load(true)
}

async function exportCsv(): Promise<void> {
  try {
    await api.download('/exports/transactions.csv', `transactions-${format.today()}.csv`, queryFor(1))
    toasts.success('Export downloaded.')
  } catch (error: any) {
    toasts.error(error.message)
  }
}

function toggleAccount(id: number): void {
  const current = filters.value.account_id ?? []

  filters.value.account_id = current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]
}

function toggleCategory(id: number): void {
  const current = filters.value.category_id ?? []

  filters.value.category_id = current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]
}

const grouped = computed(() => {
  const groups = new Map<string, Transaction[]>()

  for (const transaction of transactions.value) {
    groups.set(transaction.occurred_on, [...(groups.get(transaction.occurred_on) ?? []), transaction])
  }

  return [...groups.entries()]
})

const hasMore = computed(() => (meta.value?.current_page ?? 1) < (meta.value?.last_page ?? 1))

onMounted(async () => {
  await Promise.all([reference.load(), load()])
})
</script>

<template>
  <div>
    <!-- Search and actions -->
    <div class="flex flex-wrap items-center gap-2">
      <div class="relative min-w-0 flex-1">
        <AppIcon name="search" :size="16" class="text-ink-faint absolute top-2.5 left-3" />
        <input
          v-model="filters.q"
          type="search"
          placeholder="Search merchant, note, category or amount"
          class="field pl-9"
          @input="search"
        >
      </div>

      <button type="button" class="btn-secondary relative" @click="filtersOpen = true">
        <AppIcon name="filter" :size="15" />
        Filters
        <span
          v-if="activeFilterCount > 0"
          class="bg-brand grid size-4 place-items-center rounded-full text-[10px] font-semibold text-white"
        >
          {{ activeFilterCount }}
        </span>
      </button>

      <button type="button" class="btn-secondary" @click="exportCsv">
        <AppIcon name="download" :size="15" />
        <span class="hidden sm:inline">Export</span>
      </button>
    </div>

    <!-- Totals for the current filter -->
    <div v-if="meta" class="mt-4 grid gap-3 sm:grid-cols-4">
      <div class="card px-4 py-3">
        <p class="text-ink-soft text-xs">Matching</p>
        <p class="tnum mt-0.5 text-lg font-semibold">{{ meta.totals.count }}</p>
      </div>
      <div class="card px-4 py-3">
        <p class="text-ink-soft text-xs">Income</p>
        <MoneyAmount :minor="meta.totals.income_minor" size="lg" tone="inflow" class="mt-0.5 block" />
      </div>
      <div class="card px-4 py-3">
        <p class="text-ink-soft text-xs">Expenses</p>
        <MoneyAmount :minor="meta.totals.expense_minor" size="lg" class="text-negative mt-0.5 block" />
      </div>
      <div class="card px-4 py-3">
        <p class="text-ink-soft text-xs">Net</p>
        <MoneyAmount :minor="meta.totals.net_minor" size="lg" tone="auto" class="mt-0.5 block" />
      </div>
    </div>

    <!-- List -->
    <section class="card mt-4 overflow-hidden">
      <div v-if="pending && transactions.length === 0" class="space-y-px p-3">
        <div v-for="index in 8" :key="index" class="skeleton h-12" />
      </div>

      <EmptyState
        v-else-if="transactions.length === 0"
        icon="list"
        title="No transactions match"
        :description="activeFilterCount > 0 || filters.q ? 'Try widening the filters.' : 'Add an expense or scan a receipt to get started.'"
      >
        <button v-if="activeFilterCount > 0 || filters.q" type="button" class="btn-secondary" @click="reset">
          Clear filters
        </button>
      </EmptyState>

      <div v-else class="py-1">
        <template v-for="[day, rows] in grouped" :key="day">
          <div class="bg-raised/50 flex items-baseline justify-between px-4 py-1.5">
            <p class="text-ink-faint text-[11px] font-medium">{{ format.relativeDay(day) }}</p>
            <MoneyAmount
              :minor="rows.reduce((total, row) => total + (row.is_transfer ? 0 : row.signed_amount_minor), 0)"
              size="sm"
              tone="auto"
              sign
            />
          </div>

          <TransactionRow
            v-for="transaction in rows"
            :key="transaction.id"
            :transaction="transaction"
            @select="selected = $event"
          />
        </template>
      </div>

      <div v-if="hasMore" class="border-border border-t p-3">
        <button type="button" class="btn-secondary w-full justify-center" :disabled="pending" @click="loadMore">
          <AppIcon v-if="pending" name="refresh" :size="15" class="animate-spin" />
          Load more
        </button>
      </div>
    </section>

    <!-- Filters -->
    <AppModal :open="filtersOpen" title="Filters" width="md" @close="filtersOpen = false">
      <div class="space-y-5">
        <div>
          <span class="label">Type</span>
          <div class="bg-raised grid grid-cols-4 gap-1 rounded-lg p-1">
            <button
              v-for="option in [
                { value: 'all', label: 'All' },
                { value: 'expense', label: 'Expense' },
                { value: 'income', label: 'Income' },
                { value: 'transfer', label: 'Transfer' },
              ]"
              :key="option.value"
              type="button"
              :class="[
                'rounded-md px-2 py-1.5 text-xs font-medium transition',
                filters.type === option.value ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft',
              ]"
              @click="filters.type = option.value as any"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <div>
            <label class="label">From</label>
            <input v-model="filters.from" type="date" class="field">
          </div>
          <div>
            <label class="label">To</label>
            <input v-model="filters.to" type="date" class="field">
          </div>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <div>
            <label class="label">Minimum amount</label>
            <MoneyInput v-model="filters.min_amount as string" />
          </div>
          <div>
            <label class="label">Maximum amount</label>
            <MoneyInput v-model="filters.max_amount as string" />
          </div>
        </div>

        <div>
          <span class="label">Accounts</span>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="account in reference.activeAccounts"
              :key="account.id"
              type="button"
              :class="[
                'chip border transition',
                filters.account_id?.includes(account.id)
                  ? 'border-brand bg-brand-soft text-brand-ink'
                  : 'border-border text-ink-soft',
              ]"
              @click="toggleAccount(account.id)"
            >
              {{ account.name }}
            </button>
          </div>
        </div>

        <div>
          <span class="label">Categories</span>
          <div class="max-h-40 overflow-y-auto">
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="category in [...reference.expenseCategories, ...reference.incomeCategories]"
                :key="category.id"
                type="button"
                :class="[
                  'chip border transition',
                  filters.category_id?.includes(category.id)
                    ? 'border-brand bg-brand-soft text-brand-ink'
                    : 'border-border text-ink-soft',
                ]"
                @click="toggleCategory(category.id)"
              >
                {{ category.name }}
              </button>
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <label class="flex items-center gap-2.5 text-sm">
            <input v-model="filters.uncategorized" type="checkbox" class="accent-brand size-4 rounded">
            Only uncategorized
          </label>
          <label class="flex items-center gap-2.5 text-sm">
            <input
              type="checkbox"
              class="accent-brand size-4 rounded"
              :checked="filters.has_receipt === true"
              @change="filters.has_receipt = ($event.target as HTMLInputElement).checked ? true : undefined"
            >
            Only with a receipt
          </label>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <div>
            <label class="label">Sort by</label>
            <select v-model="filters.sort" class="field">
              <option value="occurred_on">Date</option>
              <option value="amount">Amount</option>
              <option value="merchant">Merchant</option>
              <option value="created_at">Recently added</option>
            </select>
          </div>
          <div>
            <label class="label">Order</label>
            <select v-model="filters.direction" class="field">
              <option value="desc">Descending</option>
              <option value="asc">Ascending</option>
            </select>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between gap-2">
          <button type="button" class="btn-ghost" @click="reset">Clear all</button>
          <button type="button" class="btn-primary" @click="apply">Apply filters</button>
        </div>
      </template>
    </AppModal>

    <TransactionDetail :transaction="selected" @close="selected = null" @changed="load(); reference.refresh()" />
  </div>
</template>
