<script setup lang="ts">
import type { Dashboard, Transaction } from '~/types/api'

useHead({ title: 'Home' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const auth = useAuthStore()
const reference = useReferenceStore()

const dashboard = ref<Dashboard | null>(null)
const pending = ref(true)
const selected = ref<Transaction | null>(null)
const month = ref(format.currentMonth())

async function load(): Promise<void> {
  pending.value = true

  try {
    dashboard.value = await api.get<Dashboard>('/reports/dashboard', { month: month.value })
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), reference.load()])
})

watch(month, load)

const budgetLines = computed(() =>
  [...(dashboard.value?.budget?.categories ?? [])]
    .sort((a, b) => (b.percent_used ?? 0) - (a.percent_used ?? 0))
    .slice(0, 5),
)

const attention = computed(() =>
  (dashboard.value?.budget?.categories ?? []).filter((line) => line.status !== 'ok' && line.status !== 'unset'),
)

const isCurrentMonth = computed(() => month.value === format.currentMonth())

/** Grouped by day, which is how a ledger is normally read. */
const recentByDay = computed(() => {
  const groups = new Map<string, Transaction[]>()

  for (const transaction of dashboard.value?.recent_transactions ?? []) {
    groups.set(transaction.occurred_on, [...(groups.get(transaction.occurred_on) ?? []), transaction])
  }

  return [...groups.entries()]
})
</script>

<template>
  <div>
    <!-- Month switcher -->
    <div class="mb-5 flex items-center justify-between gap-3">
      <div>
        <p class="text-ink-soft text-sm">
          {{ isCurrentMonth ? 'This month' : 'Showing' }}
        </p>
        <h2 class="text-xl font-semibold tracking-tight">{{ format.monthLabel(month) }}</h2>
      </div>

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
    </div>

    <div v-if="pending && dashboard === null" class="space-y-4">
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div v-for="index in 4" :key="index" class="skeleton h-24" />
      </div>
      <div class="skeleton h-64" />
    </div>

    <template v-else-if="dashboard">
      <!-- Cash flow -->
      <section class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Income"
          :value="dashboard.cash_flow.formatted.income"
          tone="positive"
          icon="arrow-down-left"
        />
        <StatTile
          label="Expenses"
          :value="dashboard.cash_flow.formatted.expense"
          tone="negative"
          icon="arrow-up-right"
        />
        <StatTile
          label="Saved"
          :value="dashboard.cash_flow.formatted.saved"
          icon="piggy-bank"
          tone="brand"
          hint="Set aside for goals"
        />
        <StatTile
          label="Net"
          :value="dashboard.cash_flow.formatted.net"
          :tone="dashboard.cash_flow.net_minor >= 0 ? 'positive' : 'negative'"
          icon="trending-up"
          hint="Income less spending and saving"
        />
      </section>

      <!-- Alerts -->
      <section v-if="attention.length > 0" class="mt-4">
        <div class="border-warning/30 bg-warning-soft flex flex-wrap items-center gap-x-4 gap-y-2 rounded-(--radius-card) border px-4 py-3">
          <AppIcon name="alert-triangle" :size="16" class="text-warning" />
          <p class="text-ink flex-1 text-sm">
            <template v-for="(line, index) in attention.slice(0, 2)" :key="line.id">
              {{ index > 0 ? ' · ' : '' }}
              <strong class="font-medium">{{ line.category?.name }}</strong>
              {{ line.status === 'exceeded' ? 'is over' : 'is close to' }} its limit
            </template>
            <template v-if="attention.length > 2"> and {{ attention.length - 2 }} more</template>
          </p>
          <NuxtLink to="/budget" class="text-brand-ink text-sm font-medium hover:underline">Review budget</NuxtLink>
        </div>
      </section>

      <div class="mt-4 grid gap-4 lg:grid-cols-3">
        <!-- Balances -->
        <section class="card lg:col-span-1">
          <header class="border-border flex items-center justify-between border-b px-4 py-3">
            <h3 class="text-sm font-semibold">Balances</h3>
            <NuxtLink to="/accounts" class="text-ink-faint hover:text-ink text-xs">All accounts</NuxtLink>
          </header>

          <div class="px-4 py-3">
            <p class="text-ink-soft text-xs">Available to spend</p>
            <MoneyAmount :minor="dashboard.balances.available_minor" size="xl" class="mt-0.5 block" />

            <p v-if="dashboard.balances.owed_minor > 0" class="text-ink-faint mt-1 text-xs">
              {{ dashboard.balances.formatted.owed }} owed on credit
            </p>
          </div>

          <ul class="divide-border border-border divide-y border-t">
            <li
              v-for="account in dashboard.balances.accounts"
              :key="account.id"
              class="flex items-center justify-between gap-3 px-4 py-2.5"
            >
              <span class="flex min-w-0 items-center gap-2.5">
                <span
                  class="size-2 shrink-0 rounded-full"
                  :style="{ backgroundColor: account.is_liability ? 'var(--color-negative)' : 'var(--color-positive)' }"
                />
                <span class="truncate text-sm">{{ account.name }}</span>
              </span>
              <MoneyAmount :minor="account.balance_minor" tone="auto" />
            </li>
          </ul>
        </section>

        <!-- Budget -->
        <section class="card lg:col-span-2">
          <header class="border-border flex items-center justify-between border-b px-4 py-3">
            <h3 class="text-sm font-semibold">Budget</h3>
            <NuxtLink to="/budget" class="text-ink-faint hover:text-ink text-xs">Manage</NuxtLink>
          </header>

          <div v-if="dashboard.budget === null" class="px-4">
            <EmptyState
              icon="pie-chart"
              title="No budget for this month"
              description="Set monthly limits and the app will tell you when a category is getting close."
              compact
            >
              <NuxtLink to="/budget" class="btn-primary">Set a budget</NuxtLink>
            </EmptyState>
          </div>

          <template v-else>
            <div v-if="dashboard.budget.overall?.limit_minor" class="px-4 pt-3.5">
              <div class="mb-1.5 flex items-baseline justify-between gap-3">
                <span class="text-sm font-medium">Overall</span>
                <span class="text-ink-soft text-xs">
                  <MoneyAmount :minor="dashboard.budget.overall.spent_minor" />
                  of
                  <MoneyAmount :minor="dashboard.budget.overall.limit_minor" />
                </span>
              </div>
              <ProgressMeter
                :value="dashboard.budget.overall.percent_used"
                :status="dashboard.budget.overall.status"
              />
            </div>

            <ul class="divide-border mt-3 divide-y">
              <li v-for="line in budgetLines" :key="line.id" class="px-4 py-2.5">
                <div class="flex items-center gap-3">
                  <CategoryBadge :category="line.category" size="sm" />
                  <span class="min-w-0 flex-1 truncate text-sm">{{ line.category?.name }}</span>
                  <span class="text-ink-soft tnum shrink-0 text-xs">
                    {{ line.formatted.spent }} / {{ line.formatted.limit }}
                  </span>
                </div>
                <ProgressMeter :value="line.percent_used" :status="line.status" height="sm" class="mt-1.5" />
              </li>
            </ul>
          </template>
        </section>
      </div>

      <div class="mt-4 grid gap-4 lg:grid-cols-3">
        <!-- Recent -->
        <section class="card lg:col-span-2">
          <header class="border-border flex items-center justify-between border-b px-4 py-3">
            <h3 class="text-sm font-semibold">Recent transactions</h3>
            <NuxtLink to="/transactions" class="text-ink-faint hover:text-ink text-xs">See all</NuxtLink>
          </header>

          <div v-if="recentByDay.length === 0">
            <EmptyState
              icon="list"
              title="Nothing recorded yet"
              description="Add an expense or scan a receipt to get started."
              compact
            />
          </div>

          <div v-else class="py-1">
            <template v-for="[day, transactions] in recentByDay" :key="day">
              <p class="text-ink-faint bg-raised/50 px-4 py-1 text-[11px] font-medium">
                {{ format.relativeDay(day) }}
              </p>
              <TransactionRow
                v-for="transaction in transactions"
                :key="transaction.id"
                :transaction="transaction"
                @select="selected = $event"
              />
            </template>
          </div>
        </section>

        <div class="space-y-4">
          <!-- Bills -->
          <section class="card">
            <header class="border-border flex items-center justify-between border-b px-4 py-3">
              <h3 class="text-sm font-semibold">Upcoming bills</h3>
              <NuxtLink to="/bills" class="text-ink-faint hover:text-ink text-xs">All</NuxtLink>
            </header>

            <div v-if="dashboard.upcoming_bills.length === 0">
              <EmptyState icon="calendar" title="No bills due" compact />
            </div>

            <ul v-else class="divide-border divide-y">
              <li
                v-for="bill in dashboard.upcoming_bills.slice(0, 5)"
                :key="bill.id"
                class="flex items-center justify-between gap-3 px-4 py-2.5"
              >
                <span class="min-w-0">
                  <span class="block truncate text-sm font-medium">{{ bill.name }}</span>
                  <span :class="['block text-xs', bill.is_overdue ? 'text-negative' : 'text-ink-faint']">
                    {{ format.dueLabel(bill.days_until_due) }}
                  </span>
                </span>
                <MoneyAmount :minor="bill.amount_minor" />
              </li>
            </ul>
          </section>

          <!-- Savings -->
          <section class="card">
            <header class="border-border flex items-center justify-between border-b px-4 py-3">
              <h3 class="text-sm font-semibold">Savings goals</h3>
              <NuxtLink to="/savings" class="text-ink-faint hover:text-ink text-xs">All</NuxtLink>
            </header>

            <div v-if="dashboard.savings_goals.length === 0">
              <EmptyState icon="piggy-bank" title="No goals yet" compact />
            </div>

            <ul v-else class="divide-border divide-y">
              <li v-for="goal in dashboard.savings_goals.slice(0, 4)" :key="goal.id" class="px-4 py-3">
                <div class="mb-1.5 flex items-baseline justify-between gap-3">
                  <span class="truncate text-sm font-medium">{{ goal.name }}</span>
                  <span class="text-ink-soft tnum text-xs">{{ money.percent(goal.progress) }}</span>
                </div>
                <ProgressMeter :value="goal.progress" status="goal" height="sm" />
                <p class="text-ink-faint mt-1.5 text-xs">
                  {{ goal.formatted.saved }} of {{ goal.formatted.target }}
                </p>
              </li>
            </ul>
          </section>
        </div>
      </div>

      <!-- Spending mix -->
      <section v-if="dashboard.top_categories.length > 0" class="card mt-4">
        <header class="border-border flex items-center justify-between border-b px-4 py-3">
          <h3 class="text-sm font-semibold">Where it went</h3>
          <NuxtLink to="/reports" class="text-ink-faint hover:text-ink text-xs">Full reports</NuxtLink>
        </header>

        <div class="grid gap-4 p-4 lg:grid-cols-2">
          <CategoryDonut :breakdown="dashboard.top_categories" :limit="5" />

          <ul class="space-y-2.5">
            <li v-for="entry in dashboard.top_categories" :key="entry.name">
              <div class="mb-1 flex items-baseline justify-between gap-3">
                <span class="flex min-w-0 items-center gap-2">
                  <span
                    class="size-2.5 shrink-0 rounded-sm"
                    :style="{ backgroundColor: entry.color ?? 'var(--color-border-strong)' }"
                  />
                  <span class="truncate text-sm">{{ entry.name }}</span>
                </span>
                <span class="text-ink-soft tnum shrink-0 text-xs">
                  {{ entry.formatted }} · {{ money.percent(entry.share) }}
                </span>
              </div>
              <ProgressMeter :value="entry.share" height="sm" />
            </li>
          </ul>
        </div>
      </section>
    </template>

    <TransactionDetail
      :transaction="selected"
      @close="selected = null"
      @changed="load(); reference.refresh()"
    />
  </div>
</template>
