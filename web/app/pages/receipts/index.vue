<script setup lang="ts">
import type { Paginated, Receipt } from '~/types/api'

useHead({ title: 'Receipts' })

const api = useApi()
const format = useFormat()
const toasts = useToasts()

const receipts = ref<Receipt[]>([])
const pending = ref(true)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
const filter = ref<'all' | 'review' | 'confirmed'>('all')

async function load(): Promise<void> {
  pending.value = true

  try {
    const response = await api.list<Paginated<Receipt>>('/receipts', {
      per_page: 40,
      awaiting_review: filter.value === 'review' ? 1 : undefined,
      status: filter.value === 'confirmed' ? 'confirmed' : undefined,
    })

    receipts.value = response.data
  } catch (error: any) {
    toasts.error(error.message)
  } finally {
    pending.value = false
  }
}

watch(filter, load)
onMounted(load)

async function upload(files: FileList | null): Promise<void> {
  if (files === null || files.length === 0) {
    return
  }

  uploading.value = true

  try {
    for (const file of Array.from(files)) {
      const form = new FormData()

      form.append('image', file)
      // Extraction runs inline so the review screen is ready immediately;
      // the API queues it instead when this flag is absent.
      form.append('process', '1')

      const receipt = await api.upload<Receipt>('/receipts', form)

      receipts.value = [receipt, ...receipts.value]

      if (files.length === 1) {
        await navigateTo(`/receipts/${receipt.id}`)
        return
      }
    }

    toasts.success(`${files.length} receipts uploaded.`)
  } catch (error: any) {
    toasts.error(error.field?.('image') ?? error.message)
  } finally {
    uploading.value = false

    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  upload(event.dataTransfer?.files ?? null)
}

const statusChip = (receipt: Receipt) => {
  if (receipt.status === 'confirmed') return { label: 'Saved', class: 'bg-positive-soft text-positive' }
  if (receipt.status === 'failed') return { label: 'Could not read', class: 'bg-negative-soft text-negative' }
  if (receipt.status === 'pending' || receipt.status === 'processing') {
    return { label: 'Reading…', class: 'bg-raised text-ink-soft' }
  }

  return { label: 'Needs review', class: 'bg-warning-soft text-warning' }
}

const awaitingCount = computed(() => receipts.value.filter((receipt) => receipt.status === 'processed').length)
</script>

<template>
  <div>
    <!-- Capture -->
    <div
      :class="[
        'card border-dashed p-6 text-center transition',
        dragging ? 'border-brand bg-brand-soft' : 'border-border-strong',
      ]"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="bg-brand-soft text-brand-ink mx-auto grid size-11 place-items-center rounded-xl">
        <AppIcon name="camera" :size="20" />
      </span>

      <p class="mt-3 text-sm font-medium">Scan a receipt</p>
      <p class="text-ink-faint mx-auto mt-1 max-w-md text-sm">
        Drop a photo here or choose one. The merchant, date, total and line items are
        read for you — you confirm before anything is saved.
      </p>

      <div class="mt-4 flex justify-center gap-2">
        <button type="button" class="btn-primary" :disabled="uploading" @click="fileInput?.click()">
          <AppIcon v-if="uploading" name="refresh" :size="15" class="animate-spin" />
          <AppIcon v-else name="upload" :size="15" />
          {{ uploading ? 'Uploading…' : 'Choose a photo' }}
        </button>
      </div>

      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        capture="environment"
        multiple
        class="hidden"
        @change="upload(($event.target as HTMLInputElement).files)"
      >
    </div>

    <!-- Filter -->
    <div class="mt-5 flex items-center justify-between gap-3">
      <div class="bg-raised inline-flex gap-1 rounded-lg p-1">
        <button
          v-for="option in [
            { value: 'all', label: 'All' },
            { value: 'review', label: `Needs review${awaitingCount > 0 ? ` (${awaitingCount})` : ''}` },
            { value: 'confirmed', label: 'Saved' },
          ]"
          :key="option.value"
          type="button"
          :class="[
            'rounded-md px-3 py-1.5 text-xs font-medium transition',
            filter === option.value ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft',
          ]"
          @click="filter = option.value as any"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <!-- Grid -->
    <div v-if="pending && receipts.length === 0" class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="index in 6" :key="index" class="skeleton h-44" />
    </div>

    <EmptyState
      v-else-if="receipts.length === 0"
      icon="receipt"
      title="No receipts yet"
      description="Scanned receipts appear here with their extracted details for review."
    />

    <div v-else class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NuxtLink
        v-for="receipt in receipts"
        :key="receipt.id"
        :to="`/receipts/${receipt.id}`"
        class="card group overflow-hidden transition hover:shadow-md"
      >
        <div class="bg-raised relative h-36 overflow-hidden">
          <img
            v-if="receipt.thumbnail_url ?? receipt.image_url"
            :src="receipt.thumbnail_url ?? receipt.image_url"
            :alt="`Receipt from ${receipt.merchant ?? 'unknown merchant'}`"
            class="size-full object-cover transition group-hover:scale-[1.02]"
            loading="lazy"
          >

          <span :class="['chip absolute top-2 left-2', statusChip(receipt).class]">
            {{ statusChip(receipt).label }}
          </span>

          <span
            v-if="receipt.duplicate.is_duplicate"
            class="chip bg-negative-soft text-negative absolute top-2 right-2"
            title="Possible duplicate"
          >
            <AppIcon name="alert-triangle" :size="12" />
          </span>
        </div>

        <div class="px-3.5 py-3">
          <div class="flex items-baseline justify-between gap-2">
            <p class="truncate text-sm font-medium">{{ receipt.merchant ?? 'Unknown merchant' }}</p>
            <MoneyAmount :minor="receipt.total_minor" class="shrink-0 font-medium" />
          </div>

          <p class="text-ink-faint mt-0.5 text-xs">
            {{ receipt.receipt_date ? format.date(receipt.receipt_date) : 'No date read' }}
            <template v-if="receipt.items_count"> · {{ receipt.items_count }} items</template>
          </p>
        </div>
      </NuxtLink>
    </div>
  </div>
</template>
