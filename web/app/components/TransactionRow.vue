<script setup lang="ts">
import type { Transaction } from '~/types/api'

const props = defineProps<{ transaction: Transaction; showAccount?: boolean }>()
const emit = defineEmits<{ select: [Transaction] }>()

const transaction = computed(() => props.transaction)

const title = computed(() => {
  const value = transaction.value

  if (value.is_transfer) {
    return value.direction === 'outflow'
      ? `To ${value.transfer_account?.name ?? 'another account'}`
      : `From ${value.transfer_account?.name ?? 'another account'}`
  }

  return value.merchant ?? value.category?.name ?? 'Transaction'
})

const subtitle = computed(() => {
  const value = transaction.value
  const parts: string[] = []

  if (value.is_transfer) {
    parts.push('Transfer')
  } else if (value.has_splits) {
    parts.push(`${value.splits?.length ?? 0} categories`)
  } else if (value.category) {
    parts.push(value.category.name)
  } else {
    parts.push('Uncategorized')
  }

  if (props.showAccount !== false && value.account) {
    parts.push(value.account.name)
  }

  return parts.join(' · ')
})

const tone = computed(() => {
  if (transaction.value.is_transfer) return 'none'

  return transaction.value.direction === 'inflow' ? 'inflow' : 'outflow'
})

const signedMinor = computed(() =>
  transaction.value.is_transfer
    ? transaction.value.amount_minor
    : transaction.value.signed_amount_minor,
)
</script>

<template>
  <button
    type="button"
    class="hover:bg-raised flex w-full items-center gap-3 px-3 py-2.5 text-left transition"
    @click="emit('select', transaction)"
  >
    <span
      v-if="transaction.is_transfer"
      class="bg-raised text-ink-soft grid size-9 shrink-0 place-items-center rounded-lg"
    >
      <AppIcon name="arrow-right-left" :size="16" />
    </span>
    <CategoryBadge v-else :category="transaction.category ?? null" />

    <span class="min-w-0 flex-1">
      <span class="flex items-center gap-1.5">
        <span class="truncate text-sm font-medium">{{ title }}</span>
        <AppIcon v-if="transaction.receipt_id" name="receipt" :size="13" class="text-ink-faint shrink-0" />
        <AppIcon v-if="transaction.recurring_rule_id" name="refresh" :size="13" class="text-ink-faint shrink-0" />
      </span>
      <span class="text-ink-faint block truncate text-xs">{{ subtitle }}</span>
    </span>

    <span class="shrink-0 text-right">
      <MoneyAmount
        :minor="signedMinor"
        :tone="tone"
        :sign="!transaction.is_transfer"
        class="block font-medium"
      />
      <span v-if="transaction.tags?.length" class="text-ink-faint block text-[10px]">
        {{ transaction.tags.map((tag) => tag.name).join(', ') }}
      </span>
    </span>
  </button>
</template>
