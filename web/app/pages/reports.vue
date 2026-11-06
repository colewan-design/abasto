<script setup lang="ts">
import type { CashFlow, CategoryBreakdown, MerchantTotal, MonthlyTrend } from '~/types/api'

useHead({ title: 'Reports' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()

const month = ref(format.currentMonth())
const months = ref(6)
const breakdownType = ref<'expense' | 'income'>('expense')

const cashFlow = ref<{ totals: CashFlow; daily: Array<{ date: string; income_minor: number; expense_minor: number }> } | null>(null)
const categories = ref<CategoryBreakdown[]>([])
const trends = ref<MonthlyTrend[]>([])
const merchants = ref<MerchantTotal[]>([])
const pending = ref(true)

async function load(): Promise<void> {
  pending.value = true

  try {
    const [flow, breakdown, trend, topMerchants] = await Promise.all([
      api.list<{ data: typeof cashFlow.value }>('/reports/cash-flow', { month: month.value }),
      api.list<{ data: CategoryBreakdown[] }>('/reports/categories', { month: month.value, type: breakdownType.value }),
      api.list<{ data: MonthlyTrend[] }>('/reports/trends', { months: months.value }),
      api.list<{ data: MerchantTotal[] }>('/reports/merchants', { month: month.value, limit: 10 }),
    ])

    cashFlow.value = flow.data
    categories.value = breakdown.data
    trends.value = trend.data
    merchants.value = topMerchants.data
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

onMounted(load)
watch([month, months, breakdownType], load)

async function exportPdf(): Promise<void> {
  try {
    await api.download('/exports/report.pdf', `budget-report-${month.value}.pdf`, { month: month.value })
    toasts.success('Report downloaded.')
  } catch (error: any) {
    toasts.error(error.message)
  }
}

const isCurrentMonth = computed(() => month.value === format.currentMonth())

/** Highest single day of spending, to size the sparkline bars. */
const peakDay = computed(() =>
  Math.max(1, ...(cashFlow.value?.daily ?? []).map((day) => Math.max(day.expense_minor, day.income_minor))),
)

const savingsRate = computed(() => {
  const totals = cashFlow.value?.totals

  if (!totals || totals.income_minor <= 0) {
    return null
  }

  return (totals.income_minor - totals.expense_minor) / totals.income_minor
})
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-ink-soft text-sm">{{ isCurrentMonth ? 'This month' : 'Reporting on' }}</p>
        <h2 class="text-xl font-semibold tracking-tight">{{ format.monthLabel(month) }}</h2>
      </div>

      <div class="flex items-center gap-2">
        <div class="flex items-center gap-1">
          <button type="button" class="btn-secondary p-2" aria-label="Previous month" @click="month = format.shiftMonth(month, -1)">
            <AppIcon name="chevron-left" :size="16" />
          </button>
          <button
            type="button"
            class="btn-secondary p-2"
            aria-label="Next month"
            :disabled="isCurrentMonth"
            @click="month = format.shiftMonth(month, 1)"
          >
            <AppIcon name="chevron-right" :size="16" />
          </button>
        </div>

        <button type="button" class="btn-secondary" @click="exportPdf">
          <AppIcon name="download" :size="15" />
          <span class="hidden sm:inline">PDF</span>
        </button>
      </div>
    </div>

    <div v-if="pending && cashFlow === null" class="space-y-3">
      <div class="grid gap-3 sm:grid-cols-4">
        <div v-for="index in 4" :key="index" class="skeleton h-24" />
      </div>
      <div class="skeleton h-72" />
    </div>

    <template v-else>
      <!-- Totals -->
      <section v-if="cashFlow" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Income" :value="cashFlow.totals.formatted.income" tone="positive" icon="arrow-down-left" />
        <StatTile label="Expenses" :value="cashFlow.totals.formatted.expense" tone="negative" icon="arrow-up-right" />
        <StatTile label="Saved" :value="cashFlow.totals.formatted.saved" tone="brand" icon="piggy-bank" />
        <StatTile
          label="Savings rate"
          :value="savingsRate === null ? '—' : money.percent(savingsRate, 1)"
          :tone="savingsRate !== null && savingsRate > 0 ? 'positive' : 'neutral'"
          icon="trending-up"
          hint="Of income kept"
        />
      </section>

      <!-- Daily flow -->
      <section v-if="cashFlow && cashFlow.daily.length > 0" class="card mt-4 p-4">
        <h3 class="text-sm font-semibold">Day by day</h3>
        <p class="text-ink-faint mt-0.5 text-xs">Spending below the line, income above.</p>

        <div class="mt-4 flex h-32 items-end gap-px overflow-hidden">
          <div
            v-for="day in cashFlow.daily"
            :key="day.date"
            class="group relative flex h-full flex-1 flex-col justify-end"
            :title="`${format.date(day.date)} — out ${money.format(day.expense_minor)}, in ${money.format(day.income_minor)}`"
          >
            <div
              v-if="day.income_minor > 0"
              class="bg-positive w-full rounded-t"
              :style="{ height: `${(day.income_minor / peakDay) * 45}%` }"
            />
            <div
              class="bg-negative w-full rounded-b"
              :style="{ height: `${(day.expense_minor / peakDay) * 45}%` }"
            />
          </div>
        </div>

        <div class="text-ink-faint mt-1.5 flex justify-between text-[10px]">
          <span>{{ format.date(cashFlow.daily[0]!.date, 'short') }}</span>
          <span>{{ format.date(cashFlow.daily[cashFlow.daily.length - 1]!.date, 'short') }}</span>
        </div>
      </section>

      <!-- Trends -->
      <section class="card mt-4 p-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h3 class="text-sm font-semibold">Monthly trend</h3>
            <p class="text-ink-faint mt-0.5 text-xs">Income against expenses, with net as a line.</p>
          </div>

          <select v-model.number="months" class="field w-auto py-1.5 text-xs">
            <option :value="3">3 months</option>
            <option :value="6">6 months</option>
            <option :value="12">12 months</option>
          </select>
        </div>

        <TrendChart v-if="trends.length > 0" :trends="trends" class="mt-3" />
      </section>

      <div class="mt-4 grid gap-4 lg:grid-cols-2">
        <!-- Categories -->
        <section class="card p-4">
          <div class="flex items-center justify-between gap-3">
            <h3 class="text-sm font-semibold">By category</h3>

            <div class="bg-raised inline-flex gap-1 rounded-lg p-1">
              <button
                v-for="option in [{ value: 'expense', label: 'Spending' }, { value: 'income', label: 'Income' }]"
                :key="option.value"
                type="button"
                :class="[
                  'rounded-md px-2.5 py-1 text-xs font-medium transition',
                  breakdownType === option.value ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft',
                ]"
                @click="breakdownType = option.value as any"
              >
                {{ option.label }}
              </button>
            </div>
          </div>

          <EmptyState
            v-if="categories.length === 0"
            icon="pie-chart"
            :title="`No ${breakdownType === 'income' ? 'income' : 'spending'} this month`"
            compact
          />

          <template v-else>
            <CategoryDonut :breakdown="categories" class="mt-2" />

            <ul class="divide-border mt-2 divide-y">
              <li
                v-for="entry in categories"
                :key="entry.name"
                class="flex items-center justify-between gap-3 py-2"
              >
                <span class="flex min-w-0 items-center gap-2.5">
                  <span
                    class="size-2.5 shrink-0 rounded-sm"
                    :style="{ backgroundColor: entry.color ?? 'var(--color-border-strong)' }"
                  />
                  <span class="min-w-0">
                    <span class="block truncate text-sm">{{ entry.name }}</span>
                    <span class="text-ink-faint block text-xs">
                      {{ entry.transactions }} {{ format.plural('transaction', entry.transactions) }}
                      <template v-if="entry.group"> · {{ entry.group }}</template>
                    </span>
                  </span>
                </span>

                <span class="shrink-0 text-right">
                  <MoneyAmount :minor="entry.amount_minor" class="block font-medium" />
                  <span class="text-ink-faint tnum text-xs">{{ money.percent(entry.share, 1) }}</span>
                </span>
              </li>
            </ul>
          </template>
        </section>

        <!-- Merchants -->
        <section class="card p-4">
          <h3 class="text-sm font-semibold">Top merchants</h3>
          <p class="text-ink-faint mt-0.5 text-xs">Where the money actually goes.</p>

          <EmptyState v-if="merchants.length === 0" icon="search" title="No merchant activity" compact />

          <ol v-else class="divide-border mt-2 divide-y">
            <li
              v-for="(entry, index) in merchants"
              :key="entry.merchant"
              class="flex items-center gap-3 py-2.5"
            >
              <span class="bg-raised text-ink-faint tnum grid size-6 shrink-0 place-items-center rounded-md text-xs font-semibold">
                {{ index + 1 }}
              </span>

              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ entry.merchant }}</span>
                <span class="text-ink-faint block text-xs">
                  {{ entry.visits }} {{ format.plural('visit', entry.visits) }} ·
                  <MoneyAmount :minor="entry.average_minor" size="sm" /> average
                </span>
              </span>

              <MoneyAmount :minor="entry.amount_minor" class="shrink-0 font-medium" />
            </li>
          </ol>
        </section>
      </div>
    </template>
  </div>
</template>
