// https://nuxt.com/docs/api/configuration/nuxt-config
//
// Webizon frontend — SSR-friendly Nuxt 4 app.
//
// Runtime config is split into `public` (exposed to the browser) and server-only
// values. Backend traffic is proxied through Nitro to avoid leaking the
// internal Docker hostname and to keep CORS simple in development.

export default defineNuxtConfig({
  // Nuxt 4: `srcDir: 'app/'` is the default, so every app-side file lives in
  // `app/`. Framework-agnostic code (`shared/`), build config, and the Nitro
  // server layer stay at the repo root. See CLAUDE.md §23 for the rationale.
  compatibilityDate: '2025-01-01',

  devtools: { enabled: true },
  ssr: true,

  modules: [
    '@pinia/nuxt',
    '@vueuse/nuxt',
    '@nuxtjs/tailwindcss',
    '@nuxt/eslint',
  ],

  css: ['~/assets/css/main.css'],

  // Flatten `components/**` so that `UiButton.vue` is callable as `<UiButton>`.
  // `pathPrefix: false` drops the directory segment — otherwise the ui/ folder
  // would bloat every component name to `UiUiButton`. `~` resolves to `app/`
  // under Nuxt 4, so this path still points at `app/components/**`.
  components: [
    { path: '~/components', pathPrefix: false },
  ],

  // Auto-import stores so components can just call useToastStore() etc.
  // Paths are resolved relative to srcDir (`app/`).
  imports: {
    dirs: ['stores', 'composables'],
  },

  typescript: {
    strict: true,
    typeCheck: false, // run via `npm run typecheck` in CI, not on every dev boot
  },

  runtimeConfig: {
    // Server-only: used by Nitro routeRules to proxy to the backend inside the
    // Docker network. NEVER expose this to the browser.
    backendInternalUrl: process.env.NUXT_BACKEND_INTERNAL_URL || 'http://backend:8080',

    public: {
      appName: 'Webizon',
      // Browser calls hit `/api/backend/*`, Nitro proxies to the backend.
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api/backend',
      // Centrifugo is reachable directly from the browser over WSS.
      centrifugoUrl: process.env.NUXT_PUBLIC_CENTRIFUGO_URL || 'ws://localhost:8000/connection/websocket',
      // Keycloak OIDC endpoint (public).
      keycloakUrl: process.env.NUXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8180',
      keycloakRealm: process.env.NUXT_PUBLIC_KEYCLOAK_REALM || 'webizon',
      keycloakClientId: process.env.NUXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'webizon-frontend',
    },
  },

  // Proxy browser -> Nitro -> backend. See CLAUDE.md §Docker networking.
  routeRules: {
    '/api/backend/**': {
      proxy: `${process.env.NUXT_BACKEND_INTERNAL_URL || 'http://backend:8080'}/api/**`,
    },
  },

  nitro: {
    compressPublicAssets: true,
  },

  app: {
    head: {
      htmlAttrs: { lang: 'ru' },
      title: 'Webizon — вебинары без лимитов',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Платформа вебинаров и онлайн-трансляций для школ, коучей и компаний. Оплата за факт — без абонплаты.' },
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
      ],
    },
  },
})
