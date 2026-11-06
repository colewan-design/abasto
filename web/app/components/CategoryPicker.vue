<script setup lang="ts">
import type { Category, CategoryType } from '~/types/api'

/**
 * Category selector grouped the way the catalog groups them, with a search box
 * and an inline "create" path so a missing category never blocks saving a
 * transaction.
 */
const props = withDefaults(
  defineProps<{
    modelValue: number | null
    type?: CategoryType
    /** Shown as a one-tap suggestion above the list. */
    suggestion?: Category | null
    allowCreate?: boolean
    placeholder?: string
  }>(),
  { type: 'expense', allowCreate: true, placeholder: 'Choose a category' },
)

const emit = defineEmits<{ 'update:modelValue': [number | null] }>()

const reference = useReferenceStore()
const toasts = useToasts()
const api = useApi()

const open = ref(false)
const search = ref('')
const creating = ref(false)
const newName = ref('')

const selected = computed(() => reference.categoryById(props.modelValue))

const groups = computed(() => {
  const all = props.type === 'income' ? reference.incomeCategories : reference.expenseCategories
  const term = search.value.trim().toLowerCase()
  const matching = term === '' ? all : all.filter((category) => category.name.toLowerCase().includes(term))
  const grouped = new Map<string, Category[]>()

  for (const category of matching) {
    const key = category.group?.name ?? 'Other'

    grouped.set(key, [...(grouped.get(key) ?? []), category])
  }

  return [...grouped.entries()].map(([name, categories]) => ({ name, categories }))
})

function choose(id: number | null): void {
  emit('update:modelValue', id)
  open.value = false
  search.value = ''
}

async function create(): Promise<void> {
  const name = newName.value.trim()

  if (name === '') {
    return
  }

  creating.value = true

  try {
    const category = await api.post<Category>('/categories', { name, type: props.type })

    await reference.refresh()
    choose(category.id)
    toasts.success(`Added “${category.name}”.`)
    newName.value = ''
  } catch (error: any) {
    toasts.error(error.field?.('name') ?? error.message)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div>
    <button type="button" class="field flex items-center justify-between gap-2 text-left" @click="open = true">
      <CategoryBadge v-if="selected" :category="selected" size="sm" label />
      <span v-else class="text-ink-faint">{{ placeholder }}</span>

      <AppIcon name="chevron-down" :size="16" class="text-ink-faint" />
    </button>

    <AppModal :open="open" title="Category" width="md" @close="open = false">
      <div class="relative">
        <AppIcon name="search" :size="16" class="text-ink-faint absolute top-2.5 left-3" />
        <input v-model="search" type="search" placeholder="Search categories" class="field pl-9">
      </div>

      <button
        v-if="suggestion && !search"
        type="button"
        class="border-brand/40 bg-brand-soft mt-3 flex w-full items-center gap-3 rounded-lg border px-3 py-2.5 text-left"
        @click="choose(suggestion.id)"
      >
        <CategoryBadge :category="suggestion" size="sm" />
        <span class="min-w-0 flex-1">
          <span class="block text-sm font-medium">{{ suggestion.name }}</span>
          <span class="text-brand-ink block text-xs">Suggested for this merchant</span>
        </span>
        <AppIcon name="sparkles" :size="15" class="text-brand-ink" />
      </button>

      <div class="mt-3 space-y-4">
        <div v-for="group in groups" :key="group.name">
          <p class="text-ink-faint mb-1.5 text-xs font-medium tracking-wide uppercase">{{ group.name }}</p>

          <div class="grid grid-cols-2 gap-1.5 sm:grid-cols-3">
            <button
              v-for="category in group.categories"
              :key="category.id"
              type="button"
              :class="[
                'flex items-center gap-2 rounded-lg border px-2.5 py-2 text-left text-sm transition',
                category.id === modelValue
                  ? 'border-brand bg-brand-soft text-brand-ink'
                  : 'border-border hover:border-border-strong',
              ]"
              @click="choose(category.id)"
            >
              <CategoryBadge :category="category" size="sm" />
              <span class="truncate">{{ category.name }}</span>
            </button>
          </div>
        </div>

        <p v-if="groups.length === 0" class="text-ink-faint py-4 text-center text-sm">
          No category matches “{{ search }}”.
        </p>
      </div>

      <div v-if="allowCreate" class="border-border mt-4 border-t pt-4">
        <label class="label" for="new-category">Create a category</label>
        <div class="flex gap-2">
          <input
            id="new-category"
            v-model="newName"
            type="text"
            class="field"
            :placeholder="search || 'Category name'"
            @keydown.enter.prevent="create"
          >
          <button type="button" class="btn-secondary shrink-0" :disabled="creating" @click="create">
            <AppIcon name="plus" :size="15" />
            Add
          </button>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between gap-2">
          <button type="button" class="btn-ghost" @click="choose(null)">Clear</button>
          <button type="button" class="btn-secondary" @click="open = false">Done</button>
        </div>
      </template>
    </AppModal>
  </div>
</template>
