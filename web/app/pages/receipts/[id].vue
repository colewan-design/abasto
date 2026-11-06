<script setup lang="ts">
import type { Category, Receipt, ReceiptItem } from '~/types/api'

useHead({ title: 'Review receipt' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()
const route = useRoute()

const receipt = ref<Receipt | null>(null)
const suggestedCategoryId = ref<number | null>(null)
const pending = ref(true)
const saving = ref(false)
const reprocessing = ref(false)
const acknowledged = ref(false)
const errors = ref<Record<string, string>>({})

/** Editable copy of everything the extractor read. */
const draft = ref({
  merchant: '',
  receiptDate: '',
  total: '',
  accountId: null as number | null,
  categoryId: null as number | null,
  note: '',
})

const itemCategories = ref<Record<number, number | null>>({})

async function load(): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<{ data: Receipt; meta: { suggested_category: { category_id: number | null } } }>(
      `/receipts/${route.params.id}`,
    )

    receipt.value = response.data
    suggestedCategoryId.value = response.meta?.suggested_category?.category_id ?? null

    draft.value = {
      merchant: response.data.merchant ?? '',
      receiptDate: response.data.receipt_date ?? format.today(),
      total: response.data.total_minor === null ? '' : money.toMajor(response.data.total_minor).toFixed(money.exponent.value),
      accountId: reference.activeAccounts[0]?.id ?? null,
      categoryId: suggestedCategoryId.value,
      note: '',
    }

    itemCategories.value = Object.fromEntries(
      (response.data.items ?? []).map((item) => [item.id, item.effective_category_id]),
    )
  } catch (error: any) {
    toasts.error(error.message)
    await navigateTo('/receipts')
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  await reference.load()
  await load()
})

const needsReview = computed(() => receipt.value?.extraction.needs_review ?? [])
const flagged = (field: string) => needsReview.value.includes(field)

const totalMinor = computed(() => money.parse(draft.value.total))

/**
 * How the receipt will be divided once saved: the line items grouped by the
 * category chosen for each, scaled to the receipt total so tax and discounts
 * are shared out.
 */
const splitPreview = computed(() => {
  const items = receipt.value?.items ?? []

  if (items.length === 0 || totalMinor.value === null) {
    return []
  }

  const byCategory = new Map<number | null, { amount: number; items: number }>()

  for (const item of items) {
    const categoryId = itemCategories.value[item.id] ?? null
    const current = byCategory.get(categoryId) ?? { amount: 0, items: 0 }

    byCategory.set(categoryId, { amount: current.amount + item.total_minor, items: current.items + 1 })
  }

  if (byCategory.size < 2) {
    return []
  }

  const weighted = [...byCategory.entries()]
  const sum = weighted.reduce((total, [, entry]) => total + entry.amount, 0)

  if (sum <= 0) {
    return []
  }

  // Mirrors the server's allocation: floor each share, then hand the leftover
  // minor units to the earliest rows so the parts sum to the whole.
  const shares = weighted.map(([categoryId, entry]) => ({
    categoryId,
    items: entry.items,
    amount: Math.floor((totalMinor.value! * entry.amount) / sum),
  }))

  let remainder = totalMinor.value - shares.reduce((total, share) => total + share.amount, 0)

  for (let index = 0; remainder > 0 && index < shares.length; index++, remainder--) {
    shares[index]!.amount += 1
  }

  return shares
})

const unassignedItems = computed(() =>
  (receipt.value?.items ?? []).filter((item) => (itemCategories.value[item.id] ?? null) === null).length,
)

const itemsTotal = computed(() =>
  (receipt.value?.items ?? []).reduce((total, item) => total + item.total_minor, 0),
)

const canSave = computed(
  () =>
    !saving.value &&
    draft.value.accountId !== null &&
    totalMinor.value !== null &&
    totalMinor.value > 0 &&
    receipt.value?.status !== 'confirmed' &&
    (!receipt.value?.duplicate.is_duplicate || acknowledged.value),
)

async function confirm(): Promise<void> {
  if (receipt.value === null) {
    return
  }

  saving.value = true
  errors.value = {}

  try {
    const response = await api.post<{ message: string; transaction: { id: number } }>(
      `/receipts/${receipt.value.id}/confirm`,
      {
        account_id: draft.value.accountId,
        merchant: draft.value.merchant || null,
        receipt_date: draft.value.receiptDate || null,
        total_minor: totalMinor.value,
        category_id: splitPreview.value.length > 1 ? null : draft.value.categoryId,
        note: draft.value.note || null,
        acknowledge_duplicate: acknowledged.value,
        items: (receipt.value.items ?? []).map((item) => ({
          id: item.id,
          category_id: itemCategories.value[item.id] ?? null,
        })),
      },
    )

    await reference.refresh()
    toasts.success('Receipt saved as an expense.')
    await navigateTo('/transactions')
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

async function reprocess(): Promise<void> {
  if (receipt.value === null) {
    return
  }

  reprocessing.value = true

  try {
    await api.post(`/receipts/${receipt.value.id}/reprocess`)
    await load()
    toasts.success('Receipt read again.')
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    reprocessing.value = false
  }
}

async function remove(): Promise<void> {
  if (receipt.value === null) {
    return
  }

  try {
    await api.destroy(`/receipts/${receipt.value.id}`)
    toasts.success('Receipt deleted.')
    await navigateTo('/receipts')
  } catch (error: any) {
    toasts.error(error.message)
  }
}

function categoryFor(id: number | null): Category | undefined {
  return reference.categoryById(id)
}

function applyToAll(categoryId: number | null): void {
  for (const item of receipt.value?.items ?? []) {
    itemCategories.value[item.id] = categoryId
  }
}
</script>

<template>
  <div>
    <NuxtLink to="/receipts" class="btn-ghost -ml-3 mb-3 text-sm">
      <AppIcon name="chevron-left" :size="16" />
      Receipts
    </NuxtLink>

    <div v-if="pending" class="grid gap-4 lg:grid-cols-2">
      <div class="skeleton h-96" />
      <div class="space-y-3">
        <div class="skeleton h-10" />
        <div class="skeleton h-10" />
        <div class="skeleton h-40" />
      </div>
    </div>

    <template v-else-if="receipt">
      <!-- Warnings -->
      <div
        v-if="receipt.duplicate.is_duplicate"
        class="border-negative/30 bg-negative-soft mb-4 rounded-(--radius-card) border px-4 py-3"
      >
        <div class="flex items-start gap-3">
          <AppIcon name="alert-triangle" :size="17" class="text-negative mt-0.5" />
          <div class="flex-1">
            <p class="text-negative text-sm font-medium">This looks like a duplicate</p>
            <p class="text-ink-soft mt-0.5 text-sm">{{ receipt.duplicate.reason }}</p>

            <label class="mt-2.5 flex items-center gap-2.5 text-sm">
              <input v-model="acknowledged" type="checkbox" class="accent-brand size-4 rounded">
              Save it anyway — this is a separate purchase
            </label>
          </div>
        </div>
      </div>

      <div
        v-else-if="receipt.status === 'failed'"
        class="border-warning/30 bg-warning-soft mb-4 flex items-start gap-3 rounded-(--radius-card) border px-4 py-3"
      >
        <AppIcon name="alert-triangle" :size="17" class="text-warning mt-0.5" />
        <div class="flex-1">
          <p class="text-sm font-medium">Nothing could be read from this photo</p>
          <p class="text-ink-soft mt-0.5 text-sm">
            {{ receipt.extraction.failure_reason ?? 'Fill in the details below, or try the photo again.' }}
          </p>
        </div>
        <button type="button" class="btn-secondary shrink-0" :disabled="reprocessing" @click="reprocess">
          <AppIcon name="refresh" :size="15" :class="reprocessing ? 'animate-spin' : ''" />
          Retry
        </button>
      </div>

      <div
        v-else-if="receipt.status === 'confirmed'"
        class="border-positive/30 bg-positive-soft mb-4 flex items-center gap-3 rounded-(--radius-card) border px-4 py-3"
      >
        <AppIcon name="check" :size="17" class="text-positive" />
        <p class="flex-1 text-sm">This receipt has already been saved as an expense.</p>
        <NuxtLink to="/transactions" class="text-brand-ink text-sm font-medium hover:underline">View transactions</NuxtLink>
      </div>

      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
        <!-- Image -->
        <section class="card overflow-hidden">
          <div class="bg-raised flex max-h-[32rem] items-center justify-center overflow-auto">
            <img
              :src="receipt.image_url"
              :alt="`Receipt from ${receipt.merchant ?? 'unknown merchant'}`"
              class="w-full object-contain"
            >
          </div>

          <div class="border-border text-ink-faint flex items-center justify-between gap-3 border-t px-3.5 py-2.5 text-xs">
            <span>
              Read by {{ receipt.extraction.driver ?? 'nothing yet' }}
              <template v-if="receipt.extraction.confidence">
                · {{ money.percent(receipt.extraction.confidence) }} confident
              </template>
            </span>

            <div class="flex items-center gap-1">
              <a :href="receipt.image_url" target="_blank" rel="noopener" class="btn-ghost px-2 py-1 text-xs">
                <AppIcon name="eye" :size="13" />
                Full size
              </a>
              <button type="button" class="btn-ghost px-2 py-1 text-xs" :disabled="reprocessing" @click="reprocess">
                <AppIcon name="refresh" :size="13" :class="reprocessing ? 'animate-spin' : ''" />
                Read again
              </button>
            </div>
          </div>
        </section>

        <!-- Details -->
        <div class="space-y-4">
          <section class="card p-4">
            <h2 class="text-sm font-semibold">What we read</h2>
            <p class="text-ink-faint mt-0.5 text-xs">
              Anything highlighted was unclear in the photo — worth a second look.
            </p>

            <div class="mt-3.5 space-y-3.5">
              <div>
                <label class="label" for="merchant">
                  Merchant
                  <span v-if="flagged('merchant')" class="text-warning normal-case">· check this</span>
                </label>
                <input
                  id="merchant"
                  v-model="draft.merchant"
                  type="text"
                  :class="['field', flagged('merchant') ? 'border-warning' : '']"
                  placeholder="Who you paid"
                >
              </div>

              <div class="grid gap-3 sm:grid-cols-2">
                <div>
                  <label class="label" for="receipt-date">
                    Date
                    <span v-if="flagged('receipt_date')" class="text-warning normal-case">· check this</span>
                  </label>
                  <input
                    id="receipt-date"
                    v-model="draft.receiptDate"
                    type="date"
                    :class="['field', flagged('receipt_date') ? 'border-warning' : '']"
                  >
                </div>

                <div>
                  <label class="label" for="total">
                    Total
                    <span v-if="flagged('total_minor')" class="text-warning normal-case">· check this</span>
                  </label>
                  <MoneyInput id="total" v-model="draft.total" />
                  <p v-if="errors.total_minor" class="text-negative mt-1 text-xs">{{ errors.total_minor }}</p>
                </div>
              </div>

              <dl v-if="receipt.subtotal_minor || receipt.tax_minor" class="text-ink-soft flex gap-5 text-xs">
                <div v-if="receipt.subtotal_minor">
                  <dt class="inline">Subtotal</dt>
                  <dd class="tnum ml-1 inline font-medium">{{ receipt.formatted.subtotal }}</dd>
                </div>
                <div v-if="receipt.tax_minor">
                  <dt class="inline">Tax</dt>
                  <dd class="tnum ml-1 inline font-medium">{{ receipt.formatted.tax }}</dd>
                </div>
                <div v-if="receipt.payment_method">
                  <dt class="inline">Paid by</dt>
                  <dd class="ml-1 inline font-medium">{{ receipt.payment_method }}</dd>
                </div>
              </dl>

              <div>
                <label class="label">Pay from</label>
                <AccountSelect v-model="draft.accountId" />
                <p v-if="errors.account_id" class="text-negative mt-1 text-xs">{{ errors.account_id }}</p>
              </div>

              <div v-if="splitPreview.length < 2">
                <label class="label">Category</label>
                <CategoryPicker
                  v-model="draft.categoryId"
                  type="expense"
                  :suggestion="categoryFor(suggestedCategoryId) ?? null"
                />
              </div>

              <div>
                <label class="label" for="receipt-note">Note</label>
                <input id="receipt-note" v-model="draft.note" type="text" class="field" placeholder="Optional">
              </div>
            </div>
          </section>

          <!-- Items -->
          <section v-if="receipt.items?.length" class="card">
            <header class="border-border flex items-center justify-between gap-3 border-b px-4 py-3">
              <div>
                <h2 class="text-sm font-semibold">{{ receipt.items.length }} items</h2>
                <p class="text-ink-faint mt-0.5 text-xs">
                  Items totalling <MoneyAmount :minor="itemsTotal" size="sm" />
                  <template v-if="unassignedItems > 0"> · {{ unassignedItems }} without a category</template>
                </p>
              </div>

              <button
                v-if="draft.categoryId !== null"
                type="button"
                class="btn-ghost shrink-0 px-2 py-1 text-xs"
                @click="applyToAll(draft.categoryId)"
              >
                Apply one category to all
              </button>
            </header>

            <ul class="divide-border divide-y">
              <li v-for="item in receipt.items" :key="item.id" class="px-4 py-2.5">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-sm">
                      <span v-if="item.quantity > 1" class="text-ink-faint tnum">{{ item.quantity }}× </span>
                      {{ item.description }}
                    </p>
                    <p v-if="item.unit_price_minor && item.quantity > 1" class="text-ink-faint tnum text-xs">
                      <MoneyAmount :minor="item.unit_price_minor" size="sm" /> each
                    </p>
                  </div>

                  <MoneyAmount :minor="item.total_minor" class="shrink-0" />
                </div>

                <div class="mt-2">
                  <CategoryPicker
                    v-model="itemCategories[item.id]"
                    type="expense"
                    placeholder="Category for this line"
                    :suggestion="categoryFor(item.suggested_category_id) ?? null"
                  />
                </div>
              </li>
            </ul>
          </section>

          <!-- Split preview -->
          <section v-if="splitPreview.length > 1" class="card p-4">
            <div class="flex items-start gap-2.5">
              <AppIcon name="split" :size="17" class="text-brand-ink mt-0.5" />
              <div class="min-w-0 flex-1">
                <h2 class="text-sm font-semibold">This receipt will be split</h2>
                <p class="text-ink-faint mt-0.5 text-xs">
                  One expense, one receipt, divided across categories. Tax and discounts are
                  shared out, so the parts add up to the amount charged.
                </p>

                <ul class="mt-3 space-y-2">
                  <li
                    v-for="share in splitPreview"
                    :key="String(share.categoryId)"
                    class="flex items-center justify-between gap-3"
                  >
                    <CategoryBadge :category="categoryFor(share.categoryId) ?? null" size="sm" label />
                    <span class="text-ink-faint shrink-0 text-xs">
                      {{ share.items }} {{ share.items === 1 ? 'item' : 'items' }}
                    </span>
                    <MoneyAmount :minor="share.amount" class="shrink-0 font-medium" />
                  </li>
                </ul>

                <div class="border-border mt-2.5 flex items-center justify-between border-t pt-2.5">
                  <span class="text-sm font-medium">Total</span>
                  <MoneyAmount :minor="totalMinor" class="font-semibold" />
                </div>
              </div>
            </div>
          </section>

          <!-- Actions -->
          <div class="flex items-center justify-between gap-2">
            <button type="button" class="btn-ghost text-negative" @click="remove">
              <AppIcon name="trash" :size="15" />
              Delete
            </button>

            <button type="button" class="btn-primary" :disabled="!canSave" @click="confirm">
              <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
              <AppIcon v-else name="check" :size="15" />
              Save as expense
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
