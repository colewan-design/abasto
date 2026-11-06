<script setup lang="ts">
import type { Account, Balances, Transaction } from '~/types/api'

useHead({ title: 'Accounts' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const balances = ref<Balances | null>(null)
const pending = ref(true)
const editorOpen = ref(false)
const transferOpen = ref(false)
const editing = ref<Account | null>(null)
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const openAccount = ref<Account | null>(null)
const accountTransactions = ref<Transaction[]>([])
const loadingLedger = ref(false)
const selected = ref<Transaction | null>(null)

const draft = ref({
  name: '',
  type: 'bank',
  opening: '',
  creditLimit: '',
  institution: '',
  includeInTotal: true,
})

const types = [
  { value: 'cash', label: 'Cash' },
  { value: 'bank', label: 'Bank account' },
  { value: 'ewallet', label: 'E-wallet' },
  { value: 'credit_card', label: 'Credit card' },
  { value: 'savings', label: 'Savings' },
]

async function load(): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<{ data: Account[]; meta: Balances }>('/accounts')

    reference.accounts = response.data
    balances.value = response.meta
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

onMounted(load)

function startCreate(): void {
  editing.value = null
  draft.value = { name: '', type: 'bank', opening: '', creditLimit: '', institution: '', includeInTotal: true }
  errors.value = {}
  editorOpen.value = true
}

function startEdit(account: Account): void {
  editing.value = account
  draft.value = {
    name: account.name,
    type: account.type,
    opening: money.toMajor(Math.abs(account.opening_balance_minor)).toFixed(money.exponent.value),
    creditLimit: account.credit_limit_minor === null
      ? ''
      : money.toMajor(account.credit_limit_minor).toFixed(money.exponent.value),
    institution: account.institution ?? '',
    includeInTotal: account.include_in_total,
  }
  errors.value = {}
  editorOpen.value = true
}

async function save(): Promise<void> {
  saving.value = true
  errors.value = {}

  const payload = {
    name: draft.value.name,
    type: draft.value.type,
    opening_balance_minor: money.parse(draft.value.opening) ?? 0,
    credit_limit_minor: draft.value.type === 'credit_card' ? money.parse(draft.value.creditLimit) : null,
    institution: draft.value.institution || null,
    include_in_total: draft.value.includeInTotal,
  }

  try {
    if (editing.value !== null) {
      await api.patch(`/accounts/${editing.value.id}`, payload)
      toasts.success('Account updated.')
    } else {
      await api.post('/accounts', payload)
      toasts.success('Account added.')
    }

    editorOpen.value = false
    await load()
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

async function remove(account: Account): Promise<void> {
  try {
    const response = await api.destroy<{ message: string }>(`/accounts/${account.id}`)

    toasts.success(response.message)
    editorOpen.value = false
    await load()
  } catch (error: any) {
    toasts.error(error.message)
  }
}

async function showLedger(account: Account): Promise<void> {
  openAccount.value = account
  loadingLedger.value = true

  try {
    const response = await api.list<{ data: Transaction[] }>(`/accounts/${account.id}/transactions`, { per_page: 30 })

    accountTransactions.value = response.data
  } finally {
    loadingLedger.value = false
  }
}

async function recalculate(): Promise<void> {
  try {
    const response = await api.post<{ message: string }>('/accounts/recalculate')

    toasts.success(response.message)
    await load()
  } catch (error: any) {
    toasts.error(error.message)
  }
}

const grouped = computed(() => {
  const assets = reference.accounts.filter((account) => !account.is_liability && !account.is_archived)
  const liabilities = reference.accounts.filter((account) => account.is_liability && !account.is_archived)
  const archived = reference.accounts.filter((account) => account.is_archived)

  return { assets, liabilities, archived }
})
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div v-if="balances">
        <p class="text-ink-soft text-sm">Available to spend</p>
        <MoneyAmount :minor="balances.available_minor" size="xl" class="block" />
        <p v-if="balances.owed_minor > 0" class="text-ink-faint mt-0.5 text-sm">
          {{ balances.formatted.owed }} owed · {{ balances.formatted.net_worth }} net
        </p>
      </div>

      <div class="flex gap-2">
        <button type="button" class="btn-secondary" @click="transferOpen = true">
          <AppIcon name="arrow-right-left" :size="15" />
          Transfer
        </button>
        <button type="button" class="btn-primary" @click="startCreate">
          <AppIcon name="plus" :size="15" />
          Add account
        </button>
      </div>
    </div>

    <div v-if="pending && reference.accounts.length === 0" class="grid gap-3 sm:grid-cols-2">
      <div v-for="index in 4" :key="index" class="skeleton h-24" />
    </div>

    <template v-else>
      <section v-for="section in [
        { title: 'Accounts', accounts: grouped.assets },
        { title: 'Credit', accounts: grouped.liabilities },
        { title: 'Archived', accounts: grouped.archived },
      ].filter((entry) => entry.accounts.length > 0)" :key="section.title" class="mb-5">
        <h3 class="text-ink-soft mb-2 text-xs font-medium tracking-wide uppercase">{{ section.title }}</h3>

        <div class="grid gap-3 sm:grid-cols-2">
          <div v-for="account in section.accounts" :key="account.id" class="card p-4">
            <div class="flex items-start justify-between gap-3">
              <button type="button" class="min-w-0 flex-1 text-left" @click="showLedger(account)">
                <span class="flex items-center gap-2">
                  <span
                    class="size-2.5 shrink-0 rounded-full"
                    :style="{ backgroundColor: account.color ?? 'var(--color-border-strong)' }"
                  />
                  <span class="truncate text-sm font-medium">{{ account.name }}</span>
                </span>

                <span class="text-ink-faint mt-0.5 block text-xs">
                  {{ account.type_label }}
                  <template v-if="account.institution"> · {{ account.institution }}</template>
                  <template v-if="account.transactions_count != null">
                    · {{ account.transactions_count }} transactions
                  </template>
                </span>
              </button>

              <button type="button" class="btn-ghost -mt-1 -mr-1.5 p-1.5" aria-label="Edit account" @click="startEdit(account)">
                <AppIcon name="edit" :size="15" />
              </button>
            </div>

            <div class="mt-3 flex items-end justify-between gap-3">
              <MoneyAmount :minor="account.current_balance_minor" size="lg" tone="auto" />

              <div v-if="account.credit_limit_minor" class="text-right">
                <p class="text-ink-faint text-xs">Available credit</p>
                <MoneyAmount :minor="account.available_credit_minor" size="sm" class="font-medium" />
              </div>
            </div>

            <ProgressMeter
              v-if="account.credit_limit_minor"
              :value="Math.abs(account.current_balance_minor) / account.credit_limit_minor"
              :status="Math.abs(account.current_balance_minor) / account.credit_limit_minor > 0.8 ? 'approaching' : 'ok'"
              height="sm"
              class="mt-2"
            />
          </div>
        </div>
      </section>

      <button type="button" class="btn-ghost text-xs" @click="recalculate">
        <AppIcon name="refresh" :size="14" />
        Recalculate balances from the ledger
      </button>
    </template>

    <!-- Editor -->
    <AppModal
      :open="editorOpen"
      :title="editing ? 'Edit account' : 'Add an account'"
      width="md"
      @close="editorOpen = false"
    >
      <div class="space-y-4">
        <div>
          <label class="label" for="account-name">Name</label>
          <input id="account-name" v-model="draft.name" type="text" class="field" placeholder="e.g. Savings">
          <p v-if="errors.name" class="text-negative mt-1 text-xs">{{ errors.name }}</p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label" for="account-type">Type</label>
            <select id="account-type" v-model="draft.type" class="field">
              <option v-for="type in types" :key="type.value" :value="type.value">{{ type.label }}</option>
            </select>
          </div>

          <div>
            <label class="label">
              {{ draft.type === 'credit_card' ? 'Amount owed' : 'Starting balance' }}
            </label>
            <MoneyInput v-model="draft.opening" />
          </div>
        </div>

        <div v-if="draft.type === 'credit_card'">
          <label class="label">Credit limit</label>
          <MoneyInput v-model="draft.creditLimit" />
        </div>

        <div>
          <label class="label" for="institution">Bank or provider</label>
          <input id="institution" v-model="draft.institution" type="text" class="field" placeholder="Optional">
        </div>

        <label class="flex items-center gap-2.5 text-sm">
          <input v-model="draft.includeInTotal" type="checkbox" class="accent-brand size-4 rounded">
          Include in my overall balance
        </label>

        <p class="text-ink-faint text-xs">
          Changing the starting balance recalculates this account from its transactions,
          so the running balance stays consistent with the ledger.
        </p>
      </div>

      <template #footer>
        <div class="flex items-center justify-between gap-2">
          <button v-if="editing" type="button" class="btn-ghost text-negative" @click="remove(editing)">
            <AppIcon name="trash" :size="15" />
            {{ (editing.transactions_count ?? 0) > 0 ? 'Archive' : 'Delete' }}
          </button>
          <span v-else />

          <div class="flex gap-2">
            <button type="button" class="btn-ghost" @click="editorOpen = false">Cancel</button>
            <button type="button" class="btn-primary" :disabled="saving || draft.name.trim() === ''" @click="save">
              Save
            </button>
          </div>
        </div>
      </template>
    </AppModal>

    <!-- Ledger -->
    <AppModal
      :open="openAccount !== null"
      :title="openAccount?.name ?? ''"
      :subtitle="openAccount ? `${openAccount.type_label} · ${openAccount.formatted.balance}` : undefined"
      width="lg"
      @close="openAccount = null"
    >
      <div v-if="loadingLedger" class="space-y-px">
        <div v-for="index in 6" :key="index" class="skeleton h-12" />
      </div>

      <EmptyState
        v-else-if="accountTransactions.length === 0"
        icon="list"
        title="No transactions on this account yet"
        compact
      />

      <div v-else class="-mx-2">
        <TransactionRow
          v-for="transaction in accountTransactions"
          :key="transaction.id"
          :transaction="transaction"
          :show-account="false"
          @select="selected = $event; openAccount = null"
        />
      </div>
    </AppModal>

    <TransactionComposer :open="transferOpen" mode="transfer" @close="transferOpen = false" @saved="load" />
    <TransactionDetail :transaction="selected" @close="selected = null" @changed="load" />
  </div>
</template>
