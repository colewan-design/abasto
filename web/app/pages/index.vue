<script setup lang="ts">
/**
 * The download page — the only public face of the site for now.
 *
 * What it offers comes from `public/downloads/android.json`, which
 * `./gradlew publishRelease` writes next to the APK it describes. The in-app
 * updater reads that same file, so the build advertised here and the build a
 * phone is told about cannot drift apart.
 *
 * Rather than hardcoding a promise, the page says plainly when no release has
 * been published yet.
 *
 * The look is this page's own: a green, rounded, generously spaced treatment
 * that the signed-in client does not share. It is applied by redefining the
 * theme's custom properties on the page wrapper rather than by editing
 * `main.css`, so every Tailwind utility inside retints and nothing outside
 * this file changes. See the `<style scoped>` block at the foot.
 */
definePageMeta({ layout: 'blank' })

useHead({
  title: 'Download',
  meta: [
    {
      name: 'description',
      content:
        'Budget Tracker for Android: record income and expenses, scan receipts, set category budgets, track bills and build savings.',
    },
  ],
})

const MANIFEST_PATH = '/downloads/android.json'

interface AndroidRelease {
  version_code: number
  version_name: string
  minimum_version_code?: number
  released_at?: string
  size_bytes?: number
  sha256?: string
  download_url: string
  notes?: string[]
}

const release = ref<AndroidRelease | null>(null)
const checking = ref(true)

onMounted(async () => {
  try {
    // No-store because a cached manifest is the one way this page can offer
    // a build that is no longer the current one.
    const response = await fetch(MANIFEST_PATH, { cache: 'no-store' })

    release.value = response.ok ? await response.json() : null
  } catch {
    release.value = null
  } finally {
    checking.value = false
  }
})

/**
 * `download_url` is recorded relative to the manifest so a published release
 * can be served from localhost, a LAN address or a real domain unchanged.
 */
const downloadHref = computed(() => {
  const url = release.value?.download_url

  if (!url) {
    return null
  }

  return /^https?:\/\//i.test(url) ? url : `/downloads/${url.replace(/^\.?\//, '')}`
})

const size = computed(() => {
  const bytes = release.value?.size_bytes ?? 0

  return bytes > 0 ? `${(bytes / 1_048_576).toFixed(1)} MB` : null
})

const released = computed(() => {
  const value = release.value?.released_at

  if (!value) {
    return null
  }

  // Dates are published as plain `YYYY-MM-DD`; parsing that bare would read
  // it as UTC and show the day before to anyone west of Greenwich.
  const parsed = new Date(value.length === 10 ? `${value}T00:00:00` : value)

  return Number.isNaN(parsed.getTime())
    ? null
    : parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })
})

/** The three the page leads with, each shown against a real screen. */
const headlineFeatures = [
  {
    shot: '/screenshots/activity.png',
    alt: 'The activity list, grouped by day, with money in, money out and the net for the month',
    eyebrow: 'Every peso accounted for',
    title: 'See where the money actually went',
    body: 'Record income and expenses in a few taps, or point the camera at a receipt and let the merchant, date, total and line items be read for you. Every entry lands in a category, so the month explains itself.',
    points: ['Receipt scanning with review before anything is saved', 'Split one receipt across several categories', 'Search by merchant, note or amount'],
  },
  {
    shot: '/screenshots/budget.png',
    alt: 'The budget screen showing what is left to spend and a coloured breakdown by category',
    eyebrow: 'Limits that hold',
    title: 'Budgets that warn you early',
    body: 'Set a monthly limit per category and watch what is left, not what is gone. You are told when you are getting close — not after you have gone over.',
    points: ['A limit per category, per month', 'Warned as you approach, not after', 'Suggestions based on what you actually spend'],
  },
  {
    shot: '/screenshots/insights.png',
    alt: 'The insights screen with cash-flow trends and a category breakdown',
    eyebrow: 'The long view',
    title: 'Months you can compare',
    body: 'Cash flow, category breakdowns, trends over time and the merchants quietly taking the most. Transfers between your own accounts never show up as income or as spending.',
    points: ['Cash-flow summaries month to month', 'Category and merchant breakdowns', 'Transfers that stay honest'],
  },
]

/** The rest, kept short. */
const alsoFeatures = [
  {
    icon: 'calendar',
    title: 'Bills and due dates',
    body: 'Track what is due, get reminded before it is, and mark it paid without entering the expense twice.',
  },
  {
    icon: 'piggy-bank',
    title: 'Savings goals',
    body: 'Set a target and a date, record what you put aside, and see what it takes each month to get there.',
  },
  {
    icon: 'refresh',
    title: 'Recurring entries',
    body: 'Salary, rent and subscriptions repeat on their own instead of being typed in every month.',
  },
]

/**
 * Checkable facts, not social proof.
 *
 * The reference this page's design is modelled on fills this band with store
 * rankings and awards. This build has none of that and inventing it would be
 * a lie on a public page, so the slot carries things a reader can verify for
 * themselves instead.
 */
const assurances = [
  { icon: 'shield', label: 'No ads, no trackers', detail: 'Nothing on this page or in the app reports you to anyone.' },
  { icon: 'lock', label: 'Receipts stay private', detail: 'Images are served only over a signed link that expires.' },
  { icon: 'wallet', label: '16 currencies', detail: 'Peso by default, with amounts stored in exact minor units.' },
  { icon: 'phone', label: 'Android 8.0 and later', detail: 'One APK, no Play Store account needed.' },
]

/**
 * Real reviews only. The section renders nothing while this is empty — a
 * budgeting app asking to be trusted with someone's money is the last place
 * to put words in a stranger's mouth.
 */
const reviews: { source: string; title: string; body: string; name: string }[] = []

const steps = [
  { title: 'Download the APK', body: 'Tap the button above on your phone, or transfer the file across.' },
  { title: 'Allow the install', body: 'Android will ask you to permit installs from this source. This build is not on Play yet.' },
  { title: 'Sign in or explore', body: 'Continue with Google to keep your data synced, or look around with sample data first.' },
  { title: 'Updates find you', body: 'The app checks this page for newer builds and offers to download and install them. No need to come back here.' },
]
</script>

<template>
  <div class="page bg-bg text-ink">
    <!-- Header -->
    <header class="border-border/70 bg-bg/85 sticky top-0 z-20 border-b backdrop-blur">
      <div class="mx-auto flex h-16 max-w-5xl items-center justify-between px-5 sm:px-8">
        <div class="flex items-center gap-2.5">
          <AppLogo :size="32" />
          <span class="text-[15px] font-bold tracking-tight">Budget Tracker</span>
        </div>

        <div class="flex items-center gap-1.5">
          <a href="#install" class="text-ink-soft hover:text-ink hidden rounded-full px-3.5 py-2 text-sm font-semibold transition sm:inline-flex">
            Install guide
          </a>

          <a
            :href="downloadHref ?? '#install'"
            class="bg-brand inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-bold text-white transition hover:opacity-90"
          >
            <AppIcon name="download" :size="15" />
            Download
          </a>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <section class="relative overflow-hidden">
      <!-- The reference's one flourish: a soft radial wash of the accent
           behind the hero, rather than a hard band of colour. -->
      <div class="glow pointer-events-none absolute inset-x-0 -top-40 h-136" aria-hidden="true" />

      <div class="relative mx-auto max-w-5xl px-5 pt-14 pb-12 sm:px-8 sm:pt-20">
        <div class="grid items-center gap-12 lg:grid-cols-[1.05fr_0.95fr]">
          <div>
            <span class="bg-brand-soft text-brand-ink inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-bold">
              <AppIcon name="sparkles" :size="13" />
              Free on Android
            </span>

            <!-- The break is forced so the accented phrase never splits across
                 two lines, which is where it lands at most widths. -->
            <h1 class="mt-5 text-[2.6rem] leading-[1.06] font-extrabold tracking-tight sm:text-6xl">
              Know where<br>
              <span class="text-brand">the money</span> went.
            </h1>

            <p class="text-ink-soft mt-5 max-w-xl text-lg leading-relaxed">
              Log spending in seconds, scan a receipt instead of typing it, and keep each
              month inside a budget you set. Your records sync to your own account and
              nowhere else.
            </p>

            <div class="mt-8 flex flex-wrap items-center gap-3">
              <a
                v-if="downloadHref"
                :href="downloadHref"
                download
                class="bg-brand shadow-brand/30 inline-flex items-center gap-2 rounded-full px-6 py-3.5 text-base font-bold text-white shadow-lg transition hover:opacity-90"
              >
                <AppIcon name="download" :size="18" />
                Download for Android
              </a>

              <button
                v-else
                type="button"
                class="bg-brand inline-flex items-center gap-2 rounded-full px-6 py-3.5 text-base font-bold text-white opacity-60"
                disabled
              >
                <AppIcon v-if="checking" name="refresh" :size="18" class="animate-spin" />
                <AppIcon v-else name="download" :size="18" />
                {{ checking ? 'Checking for a build…' : 'Build not published yet' }}
              </button>
            </div>

            <p class="text-ink-faint mt-4 max-w-md text-sm text-pretty">
              <template v-if="release">
                Version {{ release.version_name }}{{ size ? ` · ${size}` : '' }} · Android 8.0 and later<template v-if="released"> · published {{ released }}</template>
              </template>
              <template v-else-if="!checking">
                Run <code class="bg-raised rounded px-1.5 py-0.5 text-xs">./gradlew publishRelease</code> in
                <code class="bg-raised rounded px-1.5 py-0.5 text-xs">mobile/</code> — it builds the APK and
                writes it here with the manifest this page reads.
              </template>
            </p>

            <!-- Assurance pill: the reference puts an install count here. This
                 build has no users to count, so it states what it does not do. -->
            <div class="border-border bg-surface mt-7 inline-flex flex-wrap items-center gap-x-4 gap-y-2 rounded-full border px-4 py-2.5">
              <span class="text-ink-soft inline-flex items-center gap-1.5 text-xs font-semibold">
                <AppIcon name="check" :size="13" class="text-brand" /> No ads
              </span>
              <span class="text-ink-soft inline-flex items-center gap-1.5 text-xs font-semibold">
                <AppIcon name="check" :size="13" class="text-brand" /> No trackers
              </span>
              <span class="text-ink-soft inline-flex items-center gap-1.5 text-xs font-semibold">
                <AppIcon name="check" :size="13" class="text-brand" /> No subscription
              </span>
            </div>
          </div>

          <!-- A real screen, in a frame. Not a mockup. -->
          <div class="relative mx-auto w-full max-w-68">
            <div class="border-ink/90 bg-ink shadow-shot rounded-[2.5rem] border-10">
              <img
                src="/screenshots/home.png"
                alt="The Budget Tracker home screen, showing total balance, this month's budget and upcoming bills"
                width="540"
                height="1200"
                class="block w-full rounded-[1.9rem]"
              >
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Headline features, each against the screen it describes -->
    <section class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl space-y-20 px-5 py-16 sm:px-8 sm:py-24">
        <div
          v-for="(feature, index) in headlineFeatures"
          :key="feature.title"
          class="grid items-center gap-10 lg:grid-cols-2"
        >
          <div :class="index % 2 === 1 ? 'lg:order-2' : ''">
            <p class="text-brand text-xs font-bold tracking-widest uppercase">{{ feature.eyebrow }}</p>

            <h2 class="mt-3 text-3xl leading-tight font-extrabold tracking-tight sm:text-4xl">
              {{ feature.title }}
            </h2>

            <p class="text-ink-soft mt-4 text-base leading-relaxed">{{ feature.body }}</p>

            <ul class="mt-6 space-y-2.5">
              <li
                v-for="point in feature.points"
                :key="point"
                class="text-ink-soft flex items-start gap-2.5 text-sm font-medium"
              >
                <span class="bg-brand-soft text-brand mt-0.5 grid size-5 shrink-0 place-items-center rounded-full">
                  <AppIcon name="check" :size="12" />
                </span>
                {{ point }}
              </li>
            </ul>
          </div>

          <div :class="['mx-auto w-full max-w-60', index % 2 === 1 ? 'lg:order-1' : '']">
            <div class="border-ink/90 bg-ink shadow-shot rounded-[2.25rem] border-[9px]">
              <img
                :src="feature.shot"
                :alt="feature.alt"
                width="540"
                height="1200"
                loading="lazy"
                class="block w-full rounded-[1.7rem]"
              >
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- The rest of what it does -->
    <section class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl px-5 py-16 sm:px-8">
        <h2 class="text-2xl font-extrabold tracking-tight">And the rest of the month</h2>

        <div class="mt-8 grid gap-6 sm:grid-cols-3">
          <div v-for="feature in alsoFeatures" :key="feature.title" class="squircle bg-surface border-border border p-6">
            <span class="bg-brand-soft text-brand grid size-10 place-items-center rounded-2xl">
              <AppIcon :name="feature.icon" :size="19" />
            </span>
            <h3 class="mt-4 text-base font-bold">{{ feature.title }}</h3>
            <p class="text-ink-soft mt-1.5 text-sm leading-relaxed">{{ feature.body }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Assurances: verifiable, not awards -->
    <section class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl px-5 py-16 sm:px-8">
        <h2 class="text-2xl font-extrabold tracking-tight">What you are agreeing to</h2>
        <p class="text-ink-soft mt-2 max-w-2xl text-sm">
          This is a direct download from a small project, not a storefront listing. Here is
          the whole of it.
        </p>

        <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="item in assurances" :key="item.label" class="squircle bg-surface border-border border p-5">
            <span class="text-brand">
              <AppIcon :name="item.icon" :size="20" />
            </span>
            <p class="mt-3 text-sm font-bold">{{ item.label }}</p>
            <p class="text-ink-soft mt-1 text-sm leading-relaxed">{{ item.detail }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- On the web, too -->
    <section class="mx-auto max-w-5xl px-5 py-10 sm:px-8">
      <div class="squircle from-brand/12 to-brand/4 border-brand/15 border bg-linear-to-br p-8 sm:p-12">
        <div class="grid items-center gap-8 lg:grid-cols-[1.1fr_0.9fr]">
          <div>
            <p class="text-brand text-xs font-bold tracking-widest uppercase">Included, not extra</p>

            <h2 class="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
              Budget Tracker on the web, too.
            </h2>

            <p class="text-ink-soft mt-4 max-w-xl text-base leading-relaxed">
              The same budget, in your browser. Sign in and your accounts, categories,
              budgets, bills and history are there on any laptop — the phone and the web
              client talk to one account. Nothing extra to install, nothing more to buy.
            </p>

            <NuxtLink
              to="/app"
              class="bg-brand mt-7 inline-flex items-center gap-2 rounded-full px-5 py-3 text-sm font-bold text-white transition hover:opacity-90"
            >
              Open the web app
              <AppIcon name="chevron-right" :size="16" />
            </NuxtLink>
          </div>

          <ul class="space-y-3">
            <li
              v-for="line in [
                'Record and edit entries from a keyboard',
                'Review scanned receipts on a bigger screen',
                'Export transactions to CSV or a PDF report',
              ]"
              :key="line"
              class="text-ink-soft flex items-start gap-2.5 text-sm font-medium"
            >
              <span class="bg-brand text-white mt-0.5 grid size-5 shrink-0 place-items-center rounded-full">
                <AppIcon name="check" :size="12" />
              </span>
              {{ line }}
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!--
      Reviews. Rendered only when there are real ones to render — add them to
      the `reviews` array and the section appears. Deliberately left empty
      rather than filled with invented praise.
    -->
    <section v-if="reviews.length" class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl px-5 py-16 sm:px-8">
        <h2 class="text-2xl font-extrabold tracking-tight">What people are saying.</h2>

        <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          <figure v-for="review in reviews" :key="review.name" class="squircle bg-surface border-border border p-6">
            <figcaption class="text-ink-faint text-xs font-bold tracking-wide uppercase">
              {{ review.source }}
            </figcaption>
            <p class="mt-3 text-sm font-bold">{{ review.title }}</p>
            <blockquote class="text-ink-soft mt-2 text-sm leading-relaxed">{{ review.body }}</blockquote>
            <p class="text-ink-faint mt-4 text-xs font-semibold">{{ review.name }}</p>
          </figure>
        </div>
      </div>
    </section>

    <!-- This release -->
    <section v-if="release" class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl px-5 py-16 sm:px-8">
        <h2 class="text-2xl font-extrabold tracking-tight">
          What is new in {{ release.version_name }}
        </h2>

        <ul v-if="release.notes?.length" class="mt-6 grid gap-3 sm:grid-cols-2">
          <li
            v-for="note in release.notes"
            :key="note"
            class="text-ink-soft flex items-start gap-2.5 text-sm font-medium"
          >
            <span class="bg-brand-soft text-brand mt-0.5 grid size-5 shrink-0 place-items-center rounded-full">
              <AppIcon name="check" :size="12" />
            </span>
            {{ note }}
          </li>
        </ul>

        <p v-else class="text-ink-soft mt-2 text-sm">
          No release notes were published with this build.
        </p>

        <!--
          The app verifies this digest against what it downloads before handing
          the file to the installer. It is here so anyone downloading by hand can
          make the same check.
        -->
        <div v-if="release.sha256" class="squircle bg-raised mt-8 px-5 py-4">
          <p class="text-sm font-bold">SHA-256</p>
          <p class="text-ink-soft mt-1.5 font-mono text-xs break-all">{{ release.sha256 }}</p>
          <p class="text-ink-faint mt-2.5 text-xs">
            Build {{ release.version_code }}. Check a downloaded APK against this with
            <code class="bg-surface rounded px-1.5 py-0.5">shasum -a 256</code>.
          </p>
        </div>
      </div>
    </section>

    <!-- Install -->
    <section id="install" class="border-border/70 border-t">
      <div class="mx-auto max-w-5xl px-5 py-16 sm:px-8">
        <h2 class="text-2xl font-extrabold tracking-tight">Installing it</h2>
        <p class="text-ink-soft mt-2 text-sm">
          This is a direct APK download rather than a Play Store listing, so Android will
          ask for your permission once.
        </p>

        <ol class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <li v-for="(step, index) in steps" :key="step.title" class="squircle bg-surface border-border border p-5">
            <span class="bg-brand grid size-7 place-items-center rounded-full text-sm font-bold text-white">
              {{ index + 1 }}
            </span>
            <h3 class="mt-3.5 text-sm font-bold">{{ step.title }}</h3>
            <p class="text-ink-soft mt-1.5 text-sm leading-relaxed">{{ step.body }}</p>
          </li>
        </ol>

        <div class="squircle bg-raised mt-6 px-5 py-4">
          <p class="text-sm font-bold">Prefer to build it yourself?</p>
          <p class="text-ink-soft mt-1.5 text-sm leading-relaxed">
            The Android source is in <code class="bg-surface rounded px-1.5 py-0.5 text-xs">mobile/</code> and the API in
            <code class="bg-surface rounded px-1.5 py-0.5 text-xs">api/</code>.
            Point the app at your own server with <code class="bg-surface rounded px-1.5 py-0.5 text-xs">API_BASE_URL</code>
            in <code class="bg-surface rounded px-1.5 py-0.5 text-xs">mobile/local.properties</code>.
          </p>
        </div>
      </div>
    </section>

    <!-- Closer -->
    <section class="mx-auto max-w-5xl px-5 pt-6 pb-16 sm:px-8">
      <div class="squircle closer px-8 py-14 text-center sm:py-20">
        <h2 class="text-ink text-3xl font-extrabold tracking-tight sm:text-4xl">
          Start tracking today.
        </h2>

        <p class="text-ink-soft mx-auto mt-4 max-w-md text-base leading-relaxed">
          Record the first expense in under a minute. Explore with sample data if you would
          rather look around before signing in.
        </p>

        <a
          v-if="downloadHref"
          :href="downloadHref"
          download
          class="bg-brand mt-8 inline-flex items-center gap-2 rounded-full px-6 py-3.5 text-base font-bold text-white shadow-lg shadow-brand/30 transition hover:opacity-90"
        >
          <AppIcon name="download" :size="18" />
          Download for Android
        </a>

        <p v-else class="text-ink-faint mt-8 text-sm">No build has been published yet.</p>
      </div>
    </section>

    <footer class="border-border/70 border-t">
      <div class="text-ink-faint mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-5 py-8 text-xs sm:px-8">
        <span>Budget Tracker · a personal budgeting app for Android</span>
        <NuxtLink to="/app" class="hover:text-ink-soft font-semibold transition">Open the web app</NuxtLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/*
 * This page's palette, scoped to this page.
 *
 * Tailwind v4 compiles `bg-bg`, `text-ink-soft` and the rest to
 * `var(--color-*)` lookups, so redefining those properties here retints every
 * utility used below without touching `main.css` — the signed-in client keeps
 * the blue it has always had.
 *
 * The values are the Android app's own light scheme, lifted from
 * `mobile/.../ui/theme/Color.kt`, so the page and the screenshots on it are
 * the same coral rather than two palettes arguing. The *shape* is the
 * reference design's — large radii, long soft shadows, rounded type.
 *
 * Light only, deliberately: the reference ships light only, every screenshot
 * on this page is a light capture, and a dark page wrapped around light
 * screenshots looks like a mistake. `color-scheme` says so explicitly, so a
 * reader in dark mode gets light form controls and scrollbars to match.
 */
.page {
  color-scheme: light;

  --color-bg: #f2f3f8;
  --color-surface: #ffffff;
  --color-raised: #ecedf3;
  --color-border: #e4e6ee;
  --color-border-strong: #d5d7e1;

  --color-ink: #13141a;
  --color-ink-soft: #656876;
  --color-ink-faint: #86899a;

  --color-brand: #ff6b52;
  --color-brand-soft: #ffe8e2;
  --color-brand-ink: #8c2a19;

  --radius-card: 28px;

  /* A rounded face, in the spirit of the reference's OpenRunde. */
  --font-sans: 'Nunito', 'Inter', ui-sans-serif, system-ui, sans-serif;

  font-family: var(--font-sans);
  color: var(--color-ink);
  /* The viewport behind a short page would otherwise show the global ground. */
  background-color: var(--color-bg);
  min-height: 100vh;
}

/*
 * The reference's signature shape. `corner-shape` is a continuous curve rather
 * than a circular arc — the difference an icon grid makes. Where it is not
 * supported the large radius alone is the fallback, which is the same shape
 * the rest of the page already uses.
 */
.squircle {
  border-radius: var(--radius-card);
}

@supports (corner-shape: squircle) {
  .squircle {
    border-radius: 32px;
    corner-shape: squircle;
  }
}

/*
 * A soft wash of the accent behind the hero, not a band of colour. Mixed at a
 * low alpha rather than from `--color-brand-soft`, which sits so close to the
 * page ground that the gradient disappeared into it.
 */
.glow {
  background: radial-gradient(58% 52% at 50% 38%, rgb(255 107 82 / 0.14), transparent 70%);
}

/* Long, low-opacity shadows: the reference leans on these instead of borders. */
.shadow-shot {
  box-shadow: 0 32px 64px -32px rgb(19 20 26 / 0.4);
}

/*
 * The closing card takes after the balance card at the top of the app's home
 * screen: a pale coral wash carrying dark text, rather than white text on
 * saturated coral. White on #ff6b52 is about 2.9:1, which is not a contrast
 * ratio to set a paragraph in.
 */
.closer {
  background:
    radial-gradient(120% 140% at 12% 0%, rgb(255 255 255 / 0.75) 0%, transparent 60%),
    linear-gradient(150deg, #ffd9d0 0%, #ffe8e2 45%, #fff3ef 100%);
  box-shadow: 0 26px 44px -28px rgb(140 42 25 / 0.45);
}
</style>
