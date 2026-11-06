import { defineStore } from 'pinia'
import type { Account, Category, CategoryGroup, Tag } from '~/types/api'

/**
 * Accounts, categories and tags, loaded once and shared by every form.
 *
 * These change rarely but are needed by almost every screen, so they are
 * cached here and invalidated explicitly after a write.
 */
export const useReferenceStore = defineStore('reference', {
  state: () => ({
    accounts: [] as Account[],
    categories: [] as Category[],
    groups: [] as CategoryGroup[],
    tags: [] as Tag[],
    loaded: false,
    loading: false,
  }),

  getters: {
    activeAccounts: (state): Account[] => state.accounts.filter((account) => !account.is_archived),

    expenseCategories: (state): Category[] =>
      state.categories.filter((category) => category.type === 'expense' && !category.is_archived),

    incomeCategories: (state): Category[] =>
      state.categories.filter((category) => category.type === 'income' && !category.is_archived),

    categoryById: (state) => (id: number | null | undefined): Category | undefined =>
      id === null || id === undefined ? undefined : state.categories.find((category) => category.id === id),

    accountById: (state) => (id: number | null | undefined): Account | undefined =>
      id === null || id === undefined ? undefined : state.accounts.find((account) => account.id === id),

    /** Expense categories grouped for a picker, in the catalog's own order. */
    expenseByGroup: (state) => {
      const groups = new Map<string, Category[]>()

      for (const category of state.categories) {
        if (category.type !== 'expense' || category.is_archived) {
          continue
        }

        const key = category.group?.name ?? 'Other'

        groups.set(key, [...(groups.get(key) ?? []), category])
      }

      return [...groups.entries()].map(([name, categories]) => ({ name, categories }))
    },
  },

  actions: {
    async load(force = false): Promise<void> {
      if ((this.loaded && !force) || this.loading) {
        return
      }

      this.loading = true

      try {
        const api = useApi()
        const [accounts, categories, groups, tags] = await Promise.all([
          api.list<{ data: Account[] }>('/accounts'),
          api.list<{ data: Category[] }>('/categories'),
          api.list<{ data: CategoryGroup[] }>('/category-groups'),
          api.list<{ data: Tag[] }>('/tags'),
        ])

        this.accounts = accounts.data
        this.categories = categories.data
        this.groups = groups.data
        this.tags = tags.data
        this.loaded = true
      } finally {
        this.loading = false
      }
    },

    /** Call after any write that changes balances or the category set. */
    async refresh(): Promise<void> {
      await this.load(true)
    },
  },
})
