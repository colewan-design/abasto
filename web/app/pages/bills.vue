<script setup lang="ts">
import type { Bill } from '~/types/api'

useHead({ title: 'Bills' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const bills = ref<Bill[]>([])
const meta = ref<{ due_within_30_days_minor: number; overdue_count: number; formatted: { due_within_30_days: string } } | null>(null)
const pending = ref(true)
const editorOpen = ref(false)
const payingBill = ref<Bill | null>(null)
const editing = ref<Bill | null>(null)
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const draft = ref({
  name: '',
  payee: '',
  amount: '',
  dueDate: format.today(),
  recurrence: 'monthly',
  reminderDays: 3,
  accountId: null as number | null,
  categoryId: null as number | null,
  note: '',
})

const payment = ref({ accountId: null as number | null, amount: '', paidOn: format.today(), recordExpense: true })

const recurrences = [
  { value: 'none', label: 'One time' },
  { value: 'weekly', label: 'Weekly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'quarterly', label: 'Quarterly' },
  { value: 'semiannual', label: 'Every 6 months' },
  { value: 'yearly', label: 'Yearly' },
]

async function load(): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<{ data: Bill[]; meta: typeof meta.value }>('/bills')

    bills.value = response.data
    meta.value = response.meta
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  await Promise.all([reference.load(), load()])
})

function startCreate(): void {
  editing.value = null
  draft.value = {
    name: '',
    payee: '',
    amount: '',
    dueDate: format.today(),
    recurrence: 'monthly',
    reminderDays: 3,
    accountId: reference.activeAccounts[0]?.id ?? null,
    categoryId: null,
    note: '',
  }
  errors.value = {}
  editorOpen.value = true
}

function startEdit(bill: Bill): void {
  editing.value = bill
  draft.value = {
    name: bill.name,
    payee: bill.payee ?? '',
    amount: money.toMajor(bill.amount_minor).toFixed(money.exponent.value),
    dueDate: bill.due_date,
    recurrence: bill.recurrence,
    reminderDays: bill.reminder_days_before,
    accountId: bill.account_id,
    categoryId: bill.category_id,
    note: bill.note ?? '',
  }
  errors.value = {}
  editorOpen.value = true
}

async function save(): Promise<void> {
  saving.value = true
  errors.value = {}

  const payload = {
    name: draft.value.name,
    payee: draft.value.payee || null,
    amount_minor: money.parse(draft.value.amount),
    due_date: draft.value.dueDate,
    recurrence: draft.value.recurrence,
    reminder_days_before: draft.value.reminderDays,
    account_id: draft.value.accountId,
    category_id: draft.value.categoryId,
    note: draft.value.note || null,
  }

  try {
    if (editing.value !== null) {
      await api.patch(`/bills/${editing.value.id}`, payload)
      toasts.success('Bill updated.')
    } else {
      await api.post('/bills', payload)
      toasts.success('Bill added.')
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

function startPay(bill: Bill): void {
  payingBill.value = bill
  payment.value = {
    accountId: bill.account_id ?? reference.activeAccounts[0]?.id ?? null,
    amount: money.toMajor(bill.amount_minor).toFixed(money.exponent.value),
    paidOn: format.today(),
    recordExpense: true,
  }
}

async function pay(): Promise<void> {
  if (payingBill.value === null) {
    return
  }

  saving.value = true

  try {
    await api.post(`/bills/${payingBill.value.id}/pay`, {
      account_id: payment.value.accountId,
      amount_minor: money.parse(payment.value.amount),
      paid_on: payment.value.paidOn,
      record_expense: payment.value.recordExpense,
    })

    toasts.success(`${payingBill.value.name} marked as paid.`)
    payingBill.value = null
    await Promise.all([load(), reference.refresh()])
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}

async function skip(bill: Bill): Promise<void> {
  try {
    await api.post(`/bills/${bill.id}/skip`)
    toasts.notify(`${bill.name} skipped for this cycle.`)
    await load()
  } catch (error: any) {
    toasts.error(error.message)
  }
}

async function remove(bill: Bill): Promise<void> {
  try {
    await api.destroy(`/bills/${bill.id}`)
    toasts.success('Bill deleted.')
    editorOpen.value = false
    await load()
  } catch (error: any) {
    toasts.error(error.message)
  }
}

const sections = computed(() => {
  const overdue = bills.value.filter((bill) => bill.is_overdue)
  const soon = bills.value.filter((bill) => !bill.is_overdue && bill.days_until_due <= 14)
  const later = bills.value.filter((bill) => !bill.is_overdue && bill.days_until_due > 14)

  return [
    { title: 'Overdue', bills: overdue, tone: 'negative' as const },
    { title: 'Due soon', bills: soon, tone: 'warning' as const },
    { title: 'Later', bills: later, tone: 'neutral' as const },
  ].filter((section) => section.bills.length > 0)
})
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div v-if="meta">
        <p class="text-ink-soft text-sm">Due in the next 30 days</p>
        <MoneyAmount :minor="meta.due_within_30_days_minor" size="xl" class="block" />
        <p v-if="meta.overdue_count > 0" class="text-negative mt-0.5 text-sm">
          {{ meta.overdue_count }} {{ format.plural('bill', meta.overdue_count) }} overdue
        </p>
      </div>

      <button type="button" class="btn-primary" @click="startCreate">
        <AppIcon name="plus" :size="15" />
        Add bill
      </button>
    </div>

    <div v-if="pending && bills.length === 0" class="space-y-3">
      <div v-for="index in 4" :key="index" class="skeleton h-16" />
    </div>

    <EmptyState
      v-else-if="bills.length === 0"
      icon="calendar"
      title="No bills tracked"
      description="Add the bills you pay regularly and the app will remind you before each one is due."
    >
      <button type="button" class="btn-primary" @click="startCreate">Add your first bill</button>
    </EmptyState>

    <section v-for="section in sections" :key="section.title" class="mb-5">
      <h3
        :class="[
          'mb-2 text-xs font-medium tracking-wide uppercase',
          section.tone === 'negative' ? 'text-negative' : section.tone === 'warning' ? 'text-warning' : 'text-ink-soft',
        ]"
      >
        {{ section.title }}
      </h3>

      <ul class="card divide-border divide-y">
        <li v-for="bill in section.bills" :key="bill.id" class="flex flex-wrap items-center gap-3 px-4 py-3">
          <div class="min-w-0 flex-1">
            <p class="flex items-center gap-2 text-sm font-medium">
              {{ bill.name }}
              <span v-if="bill.recurrence !== 'none'" class="chip bg-raised text-ink-faint">
                {{ bill.recurrence_label }}
              </span>
            </p>
            <p :class="['mt-0.5 text-xs', bill.is_overdue ? 'text-negative' : 'text-ink-faint']">
              {{ format.dueLabel(bill.days_until_due) }} · {{ format.date(bill.due_date) }}
              <template v-if="bill.category"> · {{ bill.category.name }}</template>
            </p>
          </div>

          <MoneyAmount :minor="bill.amount_minor" size="lg" class="shrink-0" />

          <div class="flex shrink-0 items-center gap-1">
            <button type="button" class="btn-primary px-2.5 py-1.5 text-xs" @click="startPay(bill)">
              Mark paid
            </button>
            <button type="button" class="btn-ghost p-1.5" aria-label="Skip this cycle" title="Skip this cycle" @click="skip(bill)">
              <AppIcon name="chevron-right" :size="15" />
            </button>
            <button type="button" class="btn-ghost p-1.5" aria-label="Edit bill" @click="startEdit(bill)">
              <AppIcon name="edit" :size="15" />
            </button>
          </div>
        </li>
      </ul>
    </section>

    <!-- Editor -->
    <AppModal :open="editorOpen" :title="editing ? 'Edit bill' : 'Add a bill'" width="md" @close="editorOpen = false">
      <div class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label" for="bill-name">Name</label>
            <input id="bill-name" v-model="draft.name" type="text" class="field" placeholder="Electricity">
            <p v-if="errors.name" class="text-negative mt-1 text-xs">{{ errors.name }}</p>
          </div>

          <div>
            <label class="label" for="bill-payee">Payee</label>
            <input id="bill-payee" v-model="draft.payee" type="text" class="field" placeholder="Optional">
          </div>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label">Amount</label>
            <MoneyInput v-model="draft.amount" />
            <p v-if="errors.amount_minor" class="text-negative mt-1 text-xs">{{ errors.amount_minor }}</p>
          </div>

          <div>
            <label class="label" for="bill-due">Next due date</label>
            <input id="bill-due" v-model="draft.dueDate" type="date" class="field">
          </div>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label" for="bill-recurrence">Repeats</label>
            <select id="bill-recurrence" v-model="draft.recurrence" class="field">
              <option v-for="option in recurrences" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </div>

          <div>
            <label class="label" for="bill-reminder">Remind me</label>
            <select id="bill-reminder" v-model.number="draft.reminderDays" class="field">
              <option :value="0">On the due date</option>
              <option :value="1">1 day before</option>
              <option :value="3">3 days before</option>
              <option :value="7">A week before</option>
              <option :value="14">2 weeks before</option>
            </select>
          </div>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label">Pay from</label>
            <AccountSelect v-model="draft.accountId" />
          </div>

          <div>
            <label class="label">Category</label>
            <CategoryPicker v-model="draft.categoryId" type="expense" />
          </div>
        </div>

        <div>
          <label class="label" for="bill-note">Note</label>
          <input id="bill-note" v-model="draft.note" type="text" class="field" placeholder="Optional">
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between gap-2">
          <button v-if="editing" type="button" class="btn-ghost text-negative" @click="remove(editing)">
            <AppIcon name="trash" :size="15" />
            Delete
          </button>
          <span v-else />

          <div class="flex gap-2">
            <button type="button" class="btn-ghost" @click="editorOpen = false">Cancel</button>
            <button type="button" class="btn-primary" :disabled="saving || draft.name.trim() === ''" @click="save">Save</button>
          </div>
        </div>
      </template>
    </AppModal>

    <!-- Pay -->
    <AppModal
      :open="payingBill !== null"
      :title="`Pay ${payingBill?.name ?? ''}`"
      :subtitle="payingBill ? `Due ${format.date(payingBill.due_date)}` : undefined"
      width="sm"
      @close="payingBill = null"
    >
      <div class="space-y-4">
        <div>
          <label class="label">Amount paid</label>
          <MoneyInput v-model="payment.amount" size="lg" />
        </div>

        <div>
          <label class="label" for="paid-on">Paid on</label>
          <input id="paid-on" v-model="payment.paidOn" type="date" class="field">
        </div>

        <div v-if="payment.recordExpense">
          <label class="label">From account</label>
          <AccountSelect v-model="payment.accountId" />
        </div>

        <label class="flex items-start gap-2.5 text-sm">
          <input v-model="payment.recordExpense" type="checkbox" class="accent-brand mt-0.5 size-4 rounded">
          <span>
            Record this as an expense
            <span class="text-ink-faint block text-xs">
              Leave this off if you have already entered the payment yourself — it keeps the
              bill from being counted twice.
            </span>
          </span>
        </label>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <button type="button" class="btn-ghost" @click="payingBill = null">Cancel</button>
          <button type="button" class="btn-primary" :disabled="saving" @click="pay">
            <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
            Mark as paid
          </button>
        </div>
      </template>
    </AppModal>
  </div>
</template>
