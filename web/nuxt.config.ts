import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  compatibilityDate: '2026-11-01',
  devtools: { enabled: true },

  modules: ['@pinia/nuxt'],

  css: ['~/assets/css/main.css'],

  vite: {
    plugins: [tailwindcss()],
  },

  runtimeConfig: {
    public: {
      /**
       * Base URL of the Laravel API. Override with NUXT_PUBLIC_API_BASE.
       */
      apiBase: 'http://localhost:8010/api/v1',
      /**
       * Google OAuth client ID for Sign in with Google. Without it the
       * welcome screen offers the guest demo and password sign-in only.
       */
      googleClientId: '',
      currency: 'PHP',
    },
  },

  app: {
    head: {
      titleTemplate: '%s · Budget Tracker',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Track income and expenses, scan receipts, and keep to a budget.' },
        { name: 'theme-color', content: '#0f172a' },
      ],
      link: [
        /*
         * The app's own launcher mark, so a tab, a bookmark and a home screen
         * all show what the phone shows. `favicon.ico` carries 16/32/48 so
         * Windows shortcuts and older bookmark bars get a size they can use
         * without rescaling; the PNG is there for everything newer.
         */
        { rel: 'icon', href: '/favicon.ico', sizes: 'any' },
        { rel: 'icon', type: 'image/png', sizes: '192x192', href: '/icon-192.png' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },

        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        /*
         * Inter and the mono face carry the signed-in client. Nunito is the
         * download page's alone — a rounded face in the spirit of the one the
         * page's design is modelled on — and is referenced only by the
         * `--font-sans` override in `pages/index.vue`.
         */
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&family=Nunito:wght@400;500;600;700;800&display=swap',
        },
      ],
    },
  },

  /**
   * The API is the only source of data and every request is authenticated with
   * a bearer token held on the client, so there is nothing to render on the
   * server.
   */
  ssr: false,
})
