<script setup lang="ts">
/**
 * Platform super-admin layout with light/dark mode toggle.
 */
import {
  LayoutDashboard,
  Building2,
  Users,
  BarChart3,
  ChevronDown,
  LogOut,
  Menu,
  X,
  ShieldCheck,
  Sun,
  Moon,
} from 'lucide-vue-next'
import { Menu as HMenu, MenuButton, MenuItem, MenuItems } from '@headlessui/vue'
import { ref } from 'vue'

const auth = useAuthStore()
const { isDark, toggle: toggleTheme, hydrate } = usePlatformTheme()
const mobileOpen = ref(false)

onMounted(hydrate)

const nav = [
  { to: '/platform',         label: 'Dashboard',        icon: LayoutDashboard },
  { to: '/platform/tenants', label: 'Клиенты',          icon: Building2 },
  { to: '/platform/users',   label: 'Пользователи',     icon: Users },
  { to: '/platform/revenue', label: 'Финансы',          icon: BarChart3 },
]

function logout() {
  auth.logout()
  navigateTo('/')
}

const displayName = computed(() => auth.user?.fullName || auth.user?.email || 'Platform Admin')

const route = useRoute()
const activeNavLabel = computed(() => {
  const exact = nav.find(item => route.path === item.to)
  if (exact) return exact.label
  return nav
    .filter(item => item.to !== '/platform')
    .find(item => route.path.startsWith(item.to))?.label ?? 'Platform'
})
</script>

<template>
  <div :class="isDark ? 'dark' : ''">
    <div class="min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <UiToastContainer />

      <!-- ─── Sidebar (desktop) ─────────────────────────── -->
      <aside class="fixed inset-y-0 left-0 hidden w-56 flex-col border-r border-slate-200 bg-white lg:flex dark:border-slate-800 dark:bg-slate-900">
        <div class="flex h-16 items-center gap-3 border-b border-slate-200 px-4 dark:border-slate-800">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-violet-600 text-white">
            <ShieldCheck class="h-4 w-4" />
          </div>
          <div>
            <p class="text-sm font-bold text-slate-900 dark:text-white">Webizon</p>
            <p class="text-[10px] font-semibold uppercase tracking-widest text-violet-600 dark:text-violet-400">Super Admin</p>
          </div>
        </div>

        <nav class="flex-1 space-y-0.5 overflow-y-auto px-2 py-4">
          <NuxtLink
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
            active-class="!bg-violet-50 !text-violet-700 dark:!bg-violet-600/20 dark:!text-violet-300"
            :exact="item.to === '/platform'"
          >
            <component :is="item.icon" class="h-4 w-4 shrink-0" />
            <span>{{ item.label }}</span>
          </NuxtLink>
        </nav>

        <div class="border-t border-slate-200 p-3 dark:border-slate-800">
          <NuxtLink
            to="/admin"
            class="flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-300"
          >
            <LayoutDashboard class="h-3.5 w-3.5" />
            Вернуться в Admin
          </NuxtLink>
        </div>
      </aside>

      <!-- ─── Sidebar (mobile) ─────────────────────────── -->
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="mobileOpen"
          class="fixed inset-0 z-40 bg-black/40 dark:bg-black/60 lg:hidden"
          @click="mobileOpen = false"
        />
      </Transition>
      <Transition
        enter-active-class="transition duration-250 ease-out-expo"
        enter-from-class="-translate-x-full"
        enter-to-class="translate-x-0"
        leave-active-class="transition duration-200 ease-in"
        leave-from-class="translate-x-0"
        leave-to-class="-translate-x-full"
      >
        <aside
          v-if="mobileOpen"
          class="fixed inset-y-0 left-0 z-50 w-64 border-r border-slate-200 bg-white lg:hidden dark:border-slate-800 dark:bg-slate-900"
        >
          <div class="flex h-16 items-center justify-between gap-2 border-b border-slate-200 px-4 dark:border-slate-800">
            <div class="flex items-center gap-2">
              <div class="flex h-7 w-7 items-center justify-center rounded-lg bg-violet-600 text-white">
                <ShieldCheck class="h-3.5 w-3.5" />
              </div>
              <span class="text-sm font-bold text-slate-900 dark:text-white">Super Admin</span>
            </div>
            <button class="rounded-md p-1 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800" @click="mobileOpen = false">
              <X class="h-5 w-5" />
            </button>
          </div>
          <nav class="space-y-0.5 px-2 py-4">
            <NuxtLink
              v-for="item in nav"
              :key="item.to"
              :to="item.to"
              class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
              active-class="!bg-violet-50 !text-violet-700 dark:!bg-violet-600/20 dark:!text-violet-300"
              @click="mobileOpen = false"
            >
              <component :is="item.icon" class="h-4 w-4" />
              {{ item.label }}
            </NuxtLink>
          </nav>
        </aside>
      </Transition>

      <!-- ─── Main column ────────────────────────────── -->
      <div class="lg:pl-56">
        <header
          class="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white/80 px-4 backdrop-blur lg:px-6 dark:border-slate-800 dark:bg-slate-900/90"
        >
          <button
            class="rounded-md p-1 text-slate-500 hover:bg-slate-100 lg:hidden dark:text-slate-400 dark:hover:bg-slate-800"
            aria-label="Open menu"
            @click="mobileOpen = true"
          >
            <Menu class="h-5 w-5" />
          </button>

          <div class="hidden min-w-0 flex-col justify-center lg:flex">
            <span class="truncate text-sm font-semibold text-slate-900 leading-tight dark:text-white">
              {{ activeNavLabel }}
            </span>
            <span class="text-[10px] text-slate-500">Platform Admin</span>
          </div>

          <div class="ml-auto flex items-center gap-2">
            <!-- Theme toggle -->
            <button
              class="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:border-slate-700 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
              :title="isDark ? 'Light mode' : 'Dark mode'"
              @click="toggleTheme"
            >
              <Moon v-if="!isDark" class="h-4 w-4" />
              <Sun v-else class="h-4 w-4" />
            </button>

            <!-- User menu -->
            <HMenu as="div" class="relative">
              <MenuButton
                class="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                <span
                  class="flex h-7 w-7 items-center justify-center rounded-full bg-violet-600 text-xs font-bold text-white"
                >
                  {{ (displayName[0] ?? '?').toUpperCase() }}
                </span>
                <span class="hidden max-w-[10rem] truncate sm:inline">{{ displayName }}</span>
                <ChevronDown class="h-4 w-4 text-slate-400 dark:text-slate-500" />
              </MenuButton>
              <Transition
                enter-active-class="transition duration-150 ease-out"
                enter-from-class="opacity-0 -translate-y-1"
                enter-to-class="opacity-100 translate-y-0"
                leave-active-class="transition duration-100 ease-in"
                leave-from-class="opacity-100"
                leave-to-class="opacity-0"
              >
                <MenuItems
                  class="absolute right-0 mt-2 w-52 origin-top-right rounded-xl border border-slate-200 bg-white py-1 shadow-xl focus:outline-none dark:border-slate-700 dark:bg-slate-800"
                >
                  <div class="border-b border-slate-100 px-4 py-2 dark:border-slate-700">
                    <p class="text-sm font-semibold text-slate-900 dark:text-white">{{ displayName }}</p>
                    <p class="text-xs text-violet-600 dark:text-violet-400">Platform Admin</p>
                  </div>
                  <MenuItem v-slot="{ active }">
                    <button
                      :class="[active ? 'bg-slate-50 dark:bg-slate-700' : '', 'flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-700 dark:text-slate-300']"
                      @click="logout"
                    >
                      <LogOut class="h-4 w-4" />
                      Выйти
                    </button>
                  </MenuItem>
                </MenuItems>
              </Transition>
            </HMenu>
          </div>
        </header>

        <main class="mx-auto w-full max-w-7xl px-4 py-6 lg:px-6 lg:py-8">
          <slot />
        </main>
      </div>
    </div>
  </div>
</template>
