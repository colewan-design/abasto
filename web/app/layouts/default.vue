<script setup lang="ts">
import type { AppNotification } from '~/types/api'

const auth = useAuthStore()
const api = useApi()
const route = useRoute()

const navigation = [
  { to: '/app', label: 'Home', icon: 'home' },
  { to: '/transactions', label: 'Transactions', icon: 'list' },
  { to: '/budget', label: 'Budget', icon: 'pie-chart' },
  { to: '/accounts', label: 'Accounts', icon: 'wallet' },
  { to: '/receipts', label: 'Receipts', icon: 'receipt' },
  { to: '/bills', label: 'Bills', icon: 'calendar' },
  { to: '/savings', label: 'Savings', icon: 'piggy-bank' },
  { to: '/reports', label: 'Reports', icon: 'trending-up' },
]

// The phone-sized bar keeps the five areas the plan names; the rest stay in
// the sidebar and the "more" sheet.
const primary = computed(() => navigation.slice(0, 4))

const addOpen = ref(false)
const notificationsOpen = ref(false)
const mobileNavOpen = ref(false)

const notifications = ref<AppNotification[]>([])
const unread = ref(0)

async function loadNotifications(): Promise<void> {
  const response = await api.list<{ data: AppNotification[]; meta: { unread: number } }>('/notifications', { per_page: 12 })

  notifications.value = response.data
  unread.value = response.meta.unread
}

async function markAllRead(): Promise<void> {
  await api.post('/notifications/read-all')
  await loadNotifications()
}

onMounted(loadNotifications)

// Re-read the badge after any navigation, which is when alerts raised by a
// write on another screen become relevant.
watch(() => route.path, () => {
  mobileNavOpen.value = false
  loadNotifications()
})

const isActive = (to: string) => (to === '/app' ? route.path === '/app' : route.path.startsWith(to))
</script>

<template>
  <div class="min-h-dvh">
    <!-- Sidebar -->
    <aside class="border-border bg-surface fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r lg:flex">
      <div class="flex h-16 items-center gap-2.5 px-5">
        <AppLogo :size="32" />
        <span class="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
      </div>

      <nav class="flex-1 space-y-0.5 px-3 py-2">
        <NuxtLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
            isActive(item.to) ? 'bg-brand-soft text-brand-ink' : 'text-ink-soft hover:bg-raised hover:text-ink',
          ]"
        >
          <AppIcon :name="item.icon" :size="17" />
          {{ item.label }}
        </NuxtLink>
      </nav>

      <div class="border-border border-t p-3">
        <button type="button" class="btn-primary w-full" @click="addOpen = true">
          <AppIcon name="plus" :size="16" />
          Add transaction
        </button>

        <NuxtLink
          to="/settings"
          :class="[
            'mt-2 flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
            isActive('/settings') ? 'bg-brand-soft text-brand-ink' : 'text-ink-soft hover:bg-raised hover:text-ink',
          ]"
        >
          <AppIcon name="settings" :size="17" />
          Settings
        </NuxtLink>
      </div>
    </aside>

    <!-- Top bar -->
    <header class="border-border bg-bg/85 sticky top-0 z-20 border-b backdrop-blur lg:pl-60">
      <div class="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4 sm:px-6">
        <button type="button" class="btn-ghost -ml-1.5 p-2 lg:hidden" aria-label="Menu" @click="mobileNavOpen = true">
          <AppIcon name="menu" :size="18" />
        </button>

        <div class="min-w-0 flex-1">
          <h1 class="truncate text-base font-semibold tracking-tight">
            <slot name="title">{{ navigation.find((item) => isActive(item.to))?.label ?? 'Budget Tracker' }}</slot>
          </h1>
        </div>

        <div v-if="auth.isGuest" class="chip bg-warning-soft text-warning hidden sm:inline-flex">
          <AppIcon name="eye" :size="13" />
          Demo data
        </div>

        <button type="button" class="btn-ghost relative p-2" aria-label="Notifications" @click="notificationsOpen = true">
          <AppIcon name="bell" :size="18" />
          <span
            v-if="unread > 0"
            class="bg-negative absolute top-1 right-1 grid size-4 place-items-center rounded-full text-[9px] font-semibold text-white"
          >
            {{ unread > 9 ? '9+' : unread }}
          </span>
        </button>

        <NuxtLink to="/settings" class="btn-ghost hidden p-1 sm:inline-flex" aria-label="Account">
          <img
            v-if="auth.user?.avatar_url"
            :src="auth.user.avatar_url"
            :alt="auth.user.name"
            class="size-7 rounded-full object-cover"
          >
          <span v-else class="bg-raised text-ink-soft grid size-7 place-items-center rounded-full text-xs font-semibold">
            {{ (auth.user?.name ?? '?').slice(0, 1).toUpperCase() }}
          </span>
        </NuxtLink>
      </div>
    </header>

    <!-- Page -->
    <main class="lg:pl-60">
      <div class="mx-auto max-w-6xl px-4 pt-5 pb-28 sm:px-6 lg:pb-10">
        <slot />
      </div>
    </main>

    <!-- Phone navigation -->
    <nav class="border-border bg-surface/95 fixed inset-x-0 bottom-0 z-30 border-t backdrop-blur lg:hidden">
      <div class="mx-auto grid max-w-md grid-cols-5 items-end px-2 pt-1.5 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
        <NuxtLink
          v-for="item in primary"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex flex-col items-center gap-1 rounded-lg py-1.5 text-[10px] font-medium',
            isActive(item.to) ? 'text-brand-ink' : 'text-ink-faint',
          ]"
        >
          <AppIcon :name="item.icon" :size="20" />
          {{ item.label }}
        </NuxtLink>

        <button
          type="button"
          class="bg-brand mx-auto -mt-4 grid size-12 place-items-center rounded-full text-white shadow-lg"
          aria-label="Add"
          @click="addOpen = true"
        >
          <AppIcon name="plus" :size="22" />
        </button>
      </div>
    </nav>

    <!-- Phone drawer for the remaining areas -->
    <AppModal :open="mobileNavOpen" title="Menu" width="sm" @close="mobileNavOpen = false">
      <nav class="space-y-0.5">
        <NuxtLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium',
            isActive(item.to) ? 'bg-brand-soft text-brand-ink' : 'text-ink-soft',
          ]"
        >
          <AppIcon :name="item.icon" :size="18" />
          {{ item.label }}
        </NuxtLink>

        <NuxtLink to="/settings" class="text-ink-soft flex items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium">
          <AppIcon name="settings" :size="18" />
          Settings
        </NuxtLink>
      </nav>
    </AppModal>

    <!-- Notifications -->
    <AppModal
      :open="notificationsOpen"
      title="Notifications"
      :subtitle="unread > 0 ? `${unread} unread` : 'All caught up'"
      width="md"
      @close="notificationsOpen = false"
    >
      <div v-if="notifications.length === 0">
        <EmptyState
          icon="bell"
          title="Nothing to report"
          description="Budget alerts and bill reminders will appear here."
          compact
        />
      </div>

      <ul v-else class="divide-border -my-1 divide-y">
        <li v-for="item in notifications" :key="item.id" class="flex items-start gap-3 py-3">
          <span
            :class="[
              'mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg',
              item.type.startsWith('budget.exceeded') || item.type === 'bill.overdue'
                ? 'bg-negative-soft text-negative'
                : 'bg-warning-soft text-warning',
            ]"
          >
            <AppIcon :name="item.type.startsWith('bill') ? 'calendar' : 'pie-chart'" :size="15" />
          </span>

          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium">{{ item.title }}</p>
            <p class="text-ink-soft text-sm">{{ item.body }}</p>
          </div>

          <span v-if="!item.read" class="bg-brand mt-2 size-2 shrink-0 rounded-full" aria-label="Unread" />
        </li>
      </ul>

      <template v-if="unread > 0" #footer>
        <button type="button" class="btn-secondary w-full" @click="markAllRead">Mark all as read</button>
      </template>
    </AppModal>

    <TransactionComposer :open="addOpen" @close="addOpen = false" />
  </div>
</template>
