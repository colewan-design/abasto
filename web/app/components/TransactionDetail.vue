<script setup lang="ts">
import type { Receipt, Transaction } from '~/types/api'

/**
 * Read, edit, or delete one transaction. Deleting a transfer removes both
 * legs, which the dialog says plainly before the user commits.
 */
const props = defineProps<{ transaction: Transaction | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const editing = ref(false)
const saving = ref(false)
const confirmingDelete = ref(false)
const receipt = ref<Receipt | null>(null)

const draft = ref({ amount: '', occurredOn: '', merchant: '', note: '', categoryId: null as number | null, accountId: null as number | null })

const transaction = computed(() => props.transaction)

watch(
  () => props.transaction,
  async (value) => {
    editing.value = false
    confirmingDelete.value = false
    receipt.value = null

    if (value === null) {
      return
    }

    draft.value = {
      amount: money.toMajor(value.amount_minor).toFixed(money.exponent.value),
      occurredOn: value.occurred_on,
      merchant: value.merchant ?? '',
      note: value.note ?? '',
      categoryId: value.category_id,
      accountId: value.account_id,
    }

    if (value.receipt_id !== null) {
      try {
        receipt.value = await api.get<Receipt>(`/receipts/${value.receipt_id}`)
      } catch {
        receipt.value = null
      }
    }
  },
)

async function save(): Promise<void> {
  if (transaction.value === null) {
    return
  }

  saving.value = true

  try {
    await api.patch(`/transactions/${transaction.value.id}`, {
      amount_minor: money.parse(draft.value.amount),
      occurred_on: draft.value.occurredOn,
      merchant: draft.value.merchant || null,
      note: draft.value.note || null,
      category_id: draft.value.categoryId,
      account_id: draft.value.accountId,
    })

    toasts.success('Transaction updated.')
    emit('changed')
    emit('close')
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  if (transaction.value === null) {
    return
  }

  saving.value = true

  try {
    await api.destroy(`/transactions/${transaction.value.id}`)

    toasts.success(transaction.value.is_transfer ? 'Transfer removed.' : 'Transaction deleted.')
    emit('changed')
    emit('close')
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}

const rows = computed(() => {
  const value = transaction.value

  if (value === null) {
    return []
  }

  const entries: Array<{ label: string; value: string }> = [
    { label: 'Date', value: format.date(value.occurred_on, 'long') },
    { label: 'Account', value: value.account?.name ?? '—' },
  ]

  if (value.is_transfer) {
    entries.push({
      label: value.direction === 'outflow' ? 'To' : 'From',
      value: value.transfer_account?.name ?? '—',
    })
  } else {
    entries.push({ label: 'Category', value: value.has_splits ? 'Split' : value.category?.name ?? 'Uncategorized' })
  }

  if (value.merchant) {
    entries.push({ label: 'Merchant', value: value.merchant })
  }

  if (value.tags?.length) {
    entries.push({ label: 'Tags', value: value.tags.map((tag) => tag.name).join(', ') })
  }

  entries.push({ label: 'Added', value: value.created_via.replace(/^\w/, (c) => c.toUpperCase()) })

  return entries
})
</script>

<template>
  <AppModal
    :open="transaction !== null"
    :title="editing ? 'Edit transaction' : transaction?.merchant ?? transaction?.category?.name ?? 'Transaction'"
    :subtitle="transaction ? format.date(transaction.occurred_on) : undefined"
    @close="emit('close')"
  >
    <template v-if="transaction">
      <!-- Read -->
      <template v-if="!editing">
        <div class="text-center">
          <MoneyAmount
            :minor="transaction.is_transfer ? transaction.amount_minor : transaction.signed_amount_minor"
            :tone="transaction.is_transfer ? 'none' : 'auto'"
            size="xl"
            :sign="!transaction.is_transfer"
          />
          <p v-if="transaction.is_transfer" class="text-ink-faint mt-1 text-xs">
            Moved between your own accounts — not counted as income or spending
          </p>
          <p v-else-if="transaction.is_savings_allocation" class="text-ink-faint mt-1 text-xs">
            Set aside for a savings goal — reported apart from spending
          </p>
        </div>

        <dl class="divide-border mt-5 divide-y text-sm">
          <div v-for="row in rows" :key="row.label" class="flex items-baseline justify-between gap-4 py-2">
            <dt class="text-ink-soft">{{ row.label }}</dt>
            <dd class="text-right font-medium">{{ row.value }}</dd>
          </div>
        </dl>

        <div v-if="transaction.has_splits && transaction.splits?.length" class="mt-4">
          <p class="label">Split across</p>
          <ul class="divide-border card divide-y">
            <li
              v-for="split in transaction.splits"
              :key="split.id"
              class="flex items-center justify-between gap-3 px-3 py-2"
            >
              <CategoryBadge :category="split.category ?? null" size="sm" label />
              <MoneyAmount :minor="split.amount_minor" />
            </li>
          </ul>
        </div>

        <p v-if="transaction.note" class="bg-raised mt-4 rounded-lg px-3 py-2.5 text-sm">{{ transaction.note }}</p>

        <div v-if="receipt" class="mt-4">
          <p class="label">Receipt</p>
          <a :href="receipt.image_url" target="_blank" rel="noopener" class="card block overflow-hidden">
            <img :src="receipt.thumbnail_url ?? receipt.image_url" :alt="`Receipt from ${receipt.merchant}`" class="max-h-64 w-full object-contain">
            <span class="border-border text-ink-soft flex items-center justify-between border-t px-3 py-2 text-xs">
              {{ receipt.merchant ?? 'Receipt' }}
              <span class="text-brand-ink inline-flex items-center gap-1">
                Open
                <AppIcon name="arrow-up-right" :size="12" />
              </span>
            </span>
          </a>
        </div>
      </template>

      <!-- Edit -->
      <div v-else class="space-y-4">
        <div>
          <label class="label">Amount</label>
          <MoneyInput v-model="draft.amount" size="lg" />
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label">Date</label>
            <input v-model="draft.occurredOn" type="date" class="field">
          </div>

          <div>
            <label class="label">Account</label>
            <AccountSelect v-model="draft.accountId" />
          </div>
        </div>

        <div v-if="!transaction.is_transfer">
          <label class="label">{{ transaction.type === 'income' ? 'Source' : 'Merchant' }}</label>
          <input v-model="draft.merchant" type="text" class="field">
        </div>

        <div v-if="!transaction.is_transfer && !transaction.has_splits">
          <label class="label">Category</label>
          <CategoryPicker v-model="draft.categoryId" :type="transaction.type === 'income' ? 'income' : 'expense'" />
        </div>

        <div>
          <label class="label">Note</label>
          <textarea v-model="draft.note" rows="2" class="field resize-none" />
        </div>
      </div>
    </template>

    <template #footer>
      <div v-if="confirmingDelete" class="flex items-center justify-between gap-3">
        <p class="text-ink-soft text-sm">
          {{ transaction?.is_transfer ? 'Both sides of this transfer will be removed.' : 'Delete this transaction?' }}
        </p>
        <div class="flex gap-2">
          <button type="button" class="btn-ghost" @click="confirmingDelete = false">Keep</button>
          <button type="button" class="btn-danger" :disabled="saving" @click="remove">Delete</button>
        </div>
      </div>

      <div v-else class="flex items-center justify-between gap-2">
        <button type="button" class="btn-ghost text-negative" @click="confirmingDelete = true">
          <AppIcon name="trash" :size="15" />
          Delete
        </button>

        <div class="flex gap-2">
          <template v-if="editing">
            <button type="button" class="btn-ghost" @click="editing = false">Cancel</button>
            <button type="button" class="btn-primary" :disabled="saving" @click="save">Save changes</button>
          </template>
          <button v-else type="button" class="btn-secondary" @click="editing = true">
            <AppIcon name="edit" :size="15" />
            Edit
          </button>
        </div>
      </div>
    </template>
  </AppModal>
</template>
