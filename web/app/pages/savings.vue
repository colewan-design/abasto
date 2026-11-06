<script setup lang="ts">
import type { SavingsGoal } from '~/types/api'

useHead({ title: 'Savings' })

const api = useApi()
const money = useMoney()
const format = useFormat()
const toasts = useToasts()
const reference = useReferenceStore()

const goals = ref<SavingsGoal[]>([])
const meta = ref<{ total_target_minor: number; total_saved_minor: number; formatted: { total_target: string; total_saved: string } } | null>(null)
const pending = ref(true)
const editorOpen = ref(false)
const contributing = ref<SavingsGoal | null>(null)
const openGoal = ref<SavingsGoal | null>(null)
const editing = ref<SavingsGoal | null>(null)
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const draft = ref({ name: '', target: '', targetDate: '', accountId: null as number | null, note: '' })
const contribution = ref({ amount: '', date: format.today(), fromAccountId: null as number | null, note: '' })

async function load(): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<{ data: SavingsGoal[]; meta: typeof meta.value }>('/savings-goals')

    goals.value = response.data
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
  draft.value = { name: '', target: '', targetDate: '', accountId: null, note: '' }
  errors.value = {}
  editorOpen.value = true
}

function startEdit(goal: SavingsGoal): void {
  editing.value = goal
  draft.value = {
    name: goal.name,
    target: money.toMajor(goal.target_amount_minor).toFixed(money.exponent.value),
    targetDate: goal.target_date ?? '',
    accountId: goal.account_id,
    note: goal.note ?? '',
  }
  errors.value = {}
  editorOpen.value = true
}

async function save(): Promise<void> {
  saving.value = true
  errors.value = {}

  const payload = {
    name: draft.value.name,
    target_amount_minor: money.parse(draft.value.target),
    target_date: draft.value.targetDate || null,
    account_id: draft.value.accountId,
    note: draft.value.note || null,
  }

  try {
    if (editing.value !== null) {
      await api.patch(`/savings-goals/${editing.value.id}`, payload)
      toasts.success('Goal updated.')
    } else {
      await api.post('/savings-goals', payload)
      toasts.success('Goal created.')
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

function startContribution(goal: SavingsGoal): void {
  contributing.value = goal
  contribution.value = {
    amount: goal.suggested_monthly_minor
      ? money.toMajor(goal.suggested_monthly_minor).toFixed(money.exponent.value)
      : '',
    date: format.today(),
    fromAccountId: reference.activeAccounts[0]?.id ?? null,
    note: '',
  }
}

async function contribute(): Promise<void> {
  if (contributing.value === null) {
    return
  }

  saving.value = true

  try {
    await api.post(`/savings-goals/${contributing.value.id}/contributions`, {
      amount_minor: money.parse(contribution.value.amount),
      contributed_on: contribution.value.date,
      from_account_id: contribution.value.fromAccountId,
      note: contribution.value.note || null,
    })

    toasts.success('Contribution recorded.')
    contributing.value = null
    await Promise.all([load(), reference.refresh()])
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    saving.value = false
  }
}

async function showGoal(goal: SavingsGoal): Promise<void> {
  openGoal.value = await api.get<SavingsGoal>(`/savings-goals/${goal.id}`)
}

async function removeContribution(goalId: number, contributionId: number): Promise<void> {
  try {
    await api.destroy(`/savings-goals/${goalId}/contributions/${contributionId}`)
    toasts.success('Contribution removed.')
    openGoal.value = await api.get<SavingsGoal>(`/savings-goals/${goalId}`)
    await Promise.all([load(), reference.refresh()])
  } catch (error: any) {
    toasts.error(error.message)
  }
}

async function remove(goal: SavingsGoal): Promise<void> {
  try {
    await api.destroy(`/savings-goals/${goal.id}`)
    toasts.success('Goal deleted.')
    editorOpen.value = false
    await load()
  } catch (error: any) {
    toasts.error(error.message)
  }
}

const overallProgress = computed(() => {
  if (meta.value === null || meta.value.total_target_minor === 0) {
    return 0
  }

  return meta.value.total_saved_minor / meta.value.total_target_minor
})
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div v-if="meta" class="min-w-0">
        <p class="text-ink-soft text-sm">Saved towards goals</p>
        <p class="flex items-baseline gap-2">
          <MoneyAmount :minor="meta.total_saved_minor" size="xl" />
          <span class="text-ink-faint text-sm">of {{ meta.formatted.total_target }}</span>
        </p>
        <ProgressMeter :value="overallProgress" status="goal" height="sm" class="mt-2 max-w-xs" />
      </div>

      <button type="button" class="btn-primary" @click="startCreate">
        <AppIcon name="plus" :size="15" />
        New goal
      </button>
    </div>

    <div v-if="pending && goals.length === 0" class="grid gap-3 sm:grid-cols-2">
      <div v-for="index in 3" :key="index" class="skeleton h-40" />
    </div>

    <EmptyState
      v-else-if="goals.length === 0"
      icon="piggy-bank"
      title="No savings goals yet"
      description="Set a target, record what you put aside, and watch the gap close. Money set aside is reported apart from spending."
    >
      <button type="button" class="btn-primary" @click="startCreate">Create a goal</button>
    </EmptyState>

    <div v-else class="grid gap-3 sm:grid-cols-2">
      <div v-for="goal in goals" :key="goal.id" class="card p-4">
        <div class="flex items-start justify-between gap-3">
          <button type="button" class="min-w-0 flex-1 text-left" @click="showGoal(goal)">
            <span class="flex items-center gap-2">
              <span
                class="size-2.5 shrink-0 rounded-full"
                :style="{ backgroundColor: goal.color ?? 'var(--color-brand)' }"
              />
              <span class="truncate text-sm font-medium">{{ goal.name }}</span>
              <span v-if="goal.is_achieved" class="chip bg-positive-soft text-positive">Reached</span>
            </span>

            <span class="text-ink-faint mt-0.5 block text-xs">
              <template v-if="goal.target_date">Target {{ format.date(goal.target_date) }}</template>
              <template v-else>No target date</template>
              <template v-if="goal.contributions_count"> · {{ goal.contributions_count }} contributions</template>
            </span>
          </button>

          <button type="button" class="btn-ghost -mt-1 -mr-1.5 p-1.5" aria-label="Edit goal" @click="startEdit(goal)">
            <AppIcon name="edit" :size="15" />
          </button>
        </div>

        <div class="mt-3 flex items-baseline justify-between gap-3">
          <MoneyAmount :minor="goal.current_amount_minor" size="lg" />
          <span class="text-ink-faint tnum text-xs">
            of {{ goal.formatted.target }} · {{ money.percent(goal.progress) }}
          </span>
        </div>

        <ProgressMeter :value="goal.progress" status="goal" class="mt-2" />

        <div class="mt-3 flex items-center justify-between gap-3">
          <p class="text-ink-faint text-xs">
            <template v-if="goal.is_achieved">Target reached.</template>
            <template v-else-if="goal.formatted.suggested_monthly">
              {{ goal.formatted.suggested_monthly }} a month to reach it on time
            </template>
            <template v-else>
              {{ goal.formatted.remaining }} to go
            </template>
          </p>

          <button type="button" class="btn-secondary px-2.5 py-1.5 text-xs" @click="startContribution(goal)">
            <AppIcon name="plus" :size="14" />
            Add money
          </button>
        </div>
      </div>
    </div>

    <!-- Editor -->
    <AppModal :open="editorOpen" :title="editing ? 'Edit goal' : 'New savings goal'" width="md" @close="editorOpen = false">
      <div class="space-y-4">
        <div>
          <label class="label" for="goal-name">What are you saving for?</label>
          <input id="goal-name" v-model="draft.name" type="text" class="field" placeholder="Emergency fund">
          <p v-if="errors.name" class="text-negative mt-1 text-xs">{{ errors.name }}</p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="label">Target amount</label>
            <MoneyInput v-model="draft.target" />
            <p v-if="errors.target_amount_minor" class="text-negative mt-1 text-xs">{{ errors.target_amount_minor }}</p>
          </div>

          <div>
            <label class="label" for="goal-date">Target date</label>
            <input id="goal-date" v-model="draft.targetDate" type="date" class="field">
            <p v-if="errors.target_date" class="text-negative mt-1 text-xs">{{ errors.target_date }}</p>
          </div>
        </div>

        <div>
          <label class="label">Held in</label>
          <AccountSelect v-model="draft.accountId" />
          <p class="text-ink-faint mt-1.5 text-xs">
            Optional. Naming the account that holds this money records contributions as
            transfers into it, so balances stay accurate.
          </p>
        </div>

        <div>
          <label class="label" for="goal-note">Note</label>
          <input id="goal-note" v-model="draft.note" type="text" class="field" placeholder="Optional">
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

    <!-- Contribute -->
    <AppModal
      :open="contributing !== null"
      :title="`Add to ${contributing?.name ?? ''}`"
      :subtitle="contributing ? `${contributing.formatted.saved} of ${contributing.formatted.target} saved` : undefined"
      width="sm"
      @close="contributing = null"
    >
      <div class="space-y-4">
        <div>
          <label class="label">Amount</label>
          <MoneyInput v-model="contribution.amount" size="lg" autofocus />
        </div>

        <div>
          <label class="label" for="contribution-date">Date</label>
          <input id="contribution-date" v-model="contribution.date" type="date" class="field">
        </div>

        <div>
          <label class="label">Take it from</label>
          <AccountSelect v-model="contribution.fromAccountId" />
          <p class="text-ink-faint mt-1.5 text-xs">
            Clear this if the money was set aside outside the app and you only want the
            goal updated.
          </p>
        </div>

        <div>
          <label class="label" for="contribution-note">Note</label>
          <input id="contribution-note" v-model="contribution.note" type="text" class="field" placeholder="Optional">
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <button type="button" class="btn-ghost" @click="contributing = null">Cancel</button>
          <button type="button" class="btn-primary" :disabled="saving" @click="contribute">
            <AppIcon v-if="saving" name="refresh" :size="15" class="animate-spin" />
            Add money
          </button>
        </div>
      </template>
    </AppModal>

    <!-- History -->
    <AppModal
      :open="openGoal !== null"
      :title="openGoal?.name ?? ''"
      :subtitle="openGoal ? `${openGoal.formatted.saved} of ${openGoal.formatted.target}` : undefined"
      width="md"
      @close="openGoal = null"
    >
      <template v-if="openGoal">
        <ProgressMeter :value="openGoal.progress" status="goal" />

        <dl class="divide-border mt-4 divide-y text-sm">
          <div class="flex justify-between py-2">
            <dt class="text-ink-soft">Remaining</dt>
            <dd class="font-medium">{{ openGoal.formatted.remaining }}</dd>
          </div>
          <div v-if="openGoal.target_date" class="flex justify-between py-2">
            <dt class="text-ink-soft">Target date</dt>
            <dd class="font-medium">{{ format.date(openGoal.target_date) }}</dd>
          </div>
          <div v-if="openGoal.formatted.suggested_monthly" class="flex justify-between py-2">
            <dt class="text-ink-soft">Needed monthly</dt>
            <dd class="font-medium">{{ openGoal.formatted.suggested_monthly }}</dd>
          </div>
        </dl>

        <h3 class="mt-4 text-sm font-semibold">Contributions</h3>

        <EmptyState
          v-if="(openGoal.contributions?.length ?? 0) === 0"
          icon="piggy-bank"
          title="Nothing added yet"
          compact
        />

        <ul v-else class="divide-border mt-1 divide-y">
          <li
            v-for="entry in openGoal.contributions"
            :key="entry.id"
            class="flex items-center justify-between gap-3 py-2.5"
          >
            <div class="min-w-0">
              <p class="text-sm font-medium">{{ entry.formatted_amount }}</p>
              <p class="text-ink-faint text-xs">
                {{ format.date(entry.contributed_on) }}
                <template v-if="entry.note"> · {{ entry.note }}</template>
              </p>
            </div>

            <button
              type="button"
              class="btn-ghost p-1.5"
              aria-label="Remove contribution"
              @click="removeContribution(openGoal.id, entry.id)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </li>
        </ul>
      </template>
    </AppModal>
  </div>
</template>
