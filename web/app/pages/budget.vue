<script setup lang="ts">
import type { BudgetView } from '~/types/api'

useHead({ title: 'Budget' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const view = ref<BudgetView | null>(null)
const pending = ref(true)
const month = ref(format.currentMonth())
const editing = ref(false)
const saving = ref(false)

const overall = ref('')
const limits = ref<Record<number, string>>({})

async function load(): Promise<void> {
  pending.value = true

  try {
    view.value = await api.list<BudgetView>('/budget', { month: month.value })
    syncDraft()
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

function syncDraft(): void {
  const data = view.value

  overall.value = data?.meta.overall?.limit_minor
    ? money.toMajor(data.meta.overall.limit_minor).toFixed(money.exponent.value)
    : ''

  limits.value = Object.fromEntries(
    (data?.meta.categories ?? []).map((line) => [
      line.category_id,
      money.toMajor(line.amount_minor).toFixed(money.exponent.value),
    ]),
  )
}

onMounted(async () => {
  await Promise.all([reference.load(), load()])
})

watch(month, () => {
  editing.value = false
  load()
})

const lines = computed(() =>
  [...(view.value?.meta.categories ?? [])].sort((a, b) => (b.percent_used ?? 0) - (a.percent_used ?? 0)),
)

const isCurrentMonth = computed(() => month.value === format.currentMonth())

/** Categories that have no limit yet, offered when editing. */
const unbudgeted = computed(() => {
  const budgeted = new Set((view.value?.meta.categories ?? []).map((line) => line.category_id))

  return reference.expenseCategories.filter((category) => !budgeted.has(category.id))
})

const draftTotal = computed(() =>
  Object.values(limits.value).reduce((total, value) => total + (money.parse(value) ?? 0), 0),
)

async function save(): Promise<void> {
  saving.value = true

  try {
    await api.put('/budget', {
      month: month.value,
      overall_amount_minor: money.parse(overall.value),
      categories: Object.entries(limits.value)
        .map(([categoryId, value]) => ({ category_id: Number(categoryId), amount_minor: money.parse(value) }))
        .filter((line): line is { category_id: number; amount_minor: number } =>
          line.amount_minor !== null && line.amount_minor > 0),
    })

    await load()
    editing.value = false
    toasts.success('Budget saved.')
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}

function applySuggestions(): void {
  for (const suggestion of view.value?.meta.suggestions ?? []) {
    limits.value[suggestion.category_id] = money.toMajor(suggestion.suggested_minor).toFixed(money.exponent.value)
  }

  editing.value = true
}

const statusLabel = {
  ok: 'On track',
  approaching: 'Close to limit',
  exceeded: 'Over limit',
  unset: 'No limit',
}
</script>

<template>
  <div>
    <!-- Month -->
    <div class="mb-5 flex items-center justify-between gap-3">
      <div>
        <p class="text-ink-soft text-sm">{{ isCurrentMonth ? 'This month' : 'Budget for' }}</p>
        <h2 class="text-xl font-semibold tracking-tight">{{ format.monthLabel(month) }}</h2>
      </div>

      <div class="flex items-center gap-1">
        <button type="button" class="btn-secondary p-2" aria-label="Previous month" @click="month = format.shiftMonth(month, -1)">
          <AppIcon name="chevron-left" :size="16" />
        </button>
        <button type="button" class="btn-secondary p-2" aria-label="Next month" @click="month = format.shiftMonth(month, 1)">
          <AppIcon name="chevron-right" :size="16" />
        </button>
      </div>
    </div>

    <div v-if="pending && view === null" class="space-y-3">
      <div class="skeleton h-28" />
      <div class="skeleton h-64" />
    </div>

    <template v-else-if="view">
      <!-- No budget yet -->
      <section v-if="view.data === null && !editing" class="card">
        <EmptyState
          icon="pie-chart"
          title="No budget for this month"
          description="Set an overall limit, limits per category, or both. You will be told when a category gets close to its limit rather than after it is passed."
        >
          <button type="button" class="btn-primary" @click="editing = true">Set a budget</button>
          <button
            v-if="(view.meta.suggestions?.length ?? 0) > 0"
            type="button"
            class="btn-secondary"
            @click="applySuggestions"
          >
            <AppIcon name="sparkles" :size="15" />
            Use my past spending
          </button>
        </EmptyState>
      </section>

      <template v-else>
        <!-- Overall -->
        <section class="card p-4">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="text-ink-soft text-xs font-medium tracking-wide uppercase">Overall budget</p>

              <div v-if="editing" class="mt-2 max-w-xs">
                <MoneyInput v-model="overall" size="lg" />
              </div>

              <template v-else-if="view.meta.overall?.limit_minor">
                <p class="mt-1 flex items-baseline gap-2">
                  <MoneyAmount :minor="view.meta.overall.spent_minor" size="xl" />
                  <span class="text-ink-faint text-sm">
                    of <MoneyAmount :minor="view.meta.overall.limit_minor" size="sm" />
                  </span>
                </p>

                <p class="text-ink-soft mt-0.5 text-sm">
                  <MoneyAmount :minor="view.meta.overall.remaining_minor" tone="auto" size="sm" />
                  left this month
                </p>
              </template>

              <p v-else class="text-ink-faint mt-1 text-sm">No overall limit set.</p>
            </div>

            <div class="shrink-0 text-right">
              <span
                v-if="!editing && view.meta.overall"
                :class="[
                  'chip',
                  view.meta.overall.status === 'exceeded'
                    ? 'bg-negative-soft text-negative'
                    : view.meta.overall.status === 'approaching'
                      ? 'bg-warning-soft text-warning'
                      : 'bg-positive-soft text-positive',
                ]"
              >
                {{ statusLabel[view.meta.overall.status] }}
              </span>

              <p v-if="!editing && view.meta.overall?.percent_used != null" class="text-ink-faint tnum mt-1.5 text-xs">
                {{ money.percent(view.meta.overall.percent_used) }} used
              </p>
            </div>
          </div>

          <ProgressMeter
            v-if="!editing && view.meta.overall?.limit_minor"
            :value="view.meta.overall.percent_used"
            :status="view.meta.overall.status"
            class="mt-3"
          />

          <div
            v-if="!editing && (view.meta.unbudgeted_minor ?? 0) > 0"
            class="text-ink-faint mt-3 text-xs"
          >
            <MoneyAmount :minor="view.meta.unbudgeted_minor" size="sm" />
            spent in categories with no limit.
          </div>
        </section>

        <!-- Categories -->
        <section class="card mt-4">
          <header class="border-border flex items-center justify-between gap-3 border-b px-4 py-3">
            <h3 class="text-sm font-semibold">Category limits</h3>

            <div class="flex items-center gap-2">
              <span v-if="editing" class="text-ink-faint tnum text-xs">
                Total <MoneyAmount :minor="draftTotal" size="sm" />
              </span>

              <button v-if="!editing" type="button" class="btn-secondary px-2.5 py-1.5 text-xs" @click="editing = true">
                <AppIcon name="edit" :size="14" />
                Edit
              </button>
            </div>
          </header>

          <!-- Editing -->
          <div v-if="editing" class="divide-border divide-y">
            <div
              v-for="line in view.meta.categories ?? []"
              :key="line.category_id"
              class="flex items-center gap-3 px-4 py-2.5"
            >
              <CategoryBadge :category="line.category" size="sm" />
              <span class="min-w-0 flex-1 truncate text-sm">{{ line.category?.name }}</span>
              <div class="w-32 shrink-0">
                <MoneyInput v-model="limits[line.category_id]" />
              </div>
            </div>

            <div v-for="category in unbudgeted" :key="category.id" class="flex items-center gap-3 px-4 py-2.5">
              <CategoryBadge :category="category" size="sm" />
              <span class="text-ink-soft min-w-0 flex-1 truncate text-sm">{{ category.name }}</span>
              <div class="w-32 shrink-0">
                <MoneyInput v-model="limits[category.id]" />
              </div>
            </div>
          </div>

          <!-- Reading -->
          <div v-else-if="lines.length === 0">
            <EmptyState icon="pie-chart" title="No category limits yet" compact>
              <button type="button" class="btn-secondary" @click="editing = true">Add limits</button>
            </EmptyState>
          </div>

          <ul v-else class="divide-border divide-y">
            <li v-for="line in lines" :key="line.id" class="px-4 py-3">
              <div class="flex items-center gap-3">
                <CategoryBadge :category="line.category" size="sm" />

                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium">{{ line.category?.name }}</p>
                  <p class="text-ink-faint text-xs">
                    {{ line.formatted.spent }} of {{ line.formatted.limit }}
                    <template v-if="line.rollover_minor > 0">
                      · includes <MoneyAmount :minor="line.rollover_minor" size="sm" /> rolled over
                    </template>
                  </p>
                </div>

                <div class="shrink-0 text-right">
                  <MoneyAmount :minor="line.remaining_minor" tone="auto" class="block font-medium" />
                  <span class="text-ink-faint text-xs">
                    {{ line.remaining_minor < 0 ? 'over' : 'left' }}
                  </span>
                </div>
              </div>

              <ProgressMeter :value="line.percent_used" :status="line.status" height="sm" class="mt-2" />
            </li>
          </ul>

          <footer v-if="editing" class="border-border flex items-center justify-between gap-2 border-t px-4 py-3">
            <button type="button" class="btn-ghost" @click="editing = false; syncDraft()">Cancel</button>

            <div class="flex gap-2">
              <button
                v-if="(view.meta.suggestions?.length ?? 0) > 0"
                type="button"
                class="btn-secondary"
                @click="applySuggestions"
              >
                <AppIcon name="sparkles" :size="15" />
                Suggest from history
              </button>
              <button type="button" class="btn-primary" :disabled="saving" @click="save">
                <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
                Save budget
              </button>
            </div>
          </footer>
        </section>
      </template>
    </template>
  </div>
</template>
