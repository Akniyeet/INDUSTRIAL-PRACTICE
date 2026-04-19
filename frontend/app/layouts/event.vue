<script setup lang="ts">
/**
 * Layout used by public, tenant-owned event pages (`/e/{tenant}/{slug}` and
 * the eventual `/e/{tenant}/{slug}/room`).
 *
 * <p>Unlike {@code default.vue} this layout deliberately omits the Webizon
 * marketing chrome — visitors should feel like they are on the tenant's
 * event, not on a SaaS dashboard. The only platform-level UI we keep is the
 * toast container (so error states still surface) and a discreet "powered
 * by Webizon" footer line.
 */
const { appName } = useRuntimeConfig().public
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-white antialiased">
    <UiToastContainer />
    <main>
      <slot />
    </main>
    <footer class="border-t border-white/[0.06] py-6 text-center text-xs text-slate-500">
      Powered by <span class="font-semibold text-slate-300">{{ appName }}</span>
    </footer>
  </div>
</template>
