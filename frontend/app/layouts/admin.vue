<script setup lang="ts">
/**
 * Admin workspace layout.
 *
 * <p>Two-column desktop shell: a persistent sidebar with navigation + a main
 * area with a slim topbar that carries the tenant switcher and user menu.
 * On mobile the sidebar collapses behind a slide-over so content gets the
 * full width — webinar admins still use tablets during live sessions and
 * the control surface should not feel cramped.
 */
import {
  Calendar,
  ChevronDown,
  LayoutDashboard,
  LogOut,
  Menu,
  Megaphone,
  Radio,
  Settings,
  Users,
  X,
} from 'lucide-vue-next'
import { Menu as HMenu, MenuButton, MenuItem, MenuItems } from '@headlessui/vue'
import { ref } from 'vue'

const auth = useAuthStore()
const mobileOpen = ref(false)

const nav = [
  { to: '/admin',          label: 'Басты бет',  icon: LayoutDashboard },
  { to: '/admin/events',   label: 'Ивенттер',   icon: Calendar },
  { to: '/admin/sessions', label: 'Сессиялар',  icon: Radio },
  { to: '/admin/members',  label: 'Мүшелер',    icon: Users },
  { to: '/admin/settings', label: 'Баптаулар',  icon: Settings },
]

function logout() {
  auth.logout()
  navigateTo('/auth/sign-in')
}

const displayName = computed(() => auth.user?.fullName || auth.user?.email || 'Гость')
const tenantLabel = computed(() => auth.user?.tenantId ? 'Workspace' : 'No workspace')
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <UiToastContainer />

    <!-- ─── Sidebar (desktop) ─────────────────────────────────────────────── -->
    <aside
      class="fixed inset-y-0 left-0 hidden w-60 flex-col border-r border-slate-200 bg-white lg:flex"
    >
      <div class="flex h-16 items-center gap-2 border-b border-slate-100 px-5 font-semibold">
        <span class="inline-block h-3 w-3 rounded-full bg-brand-600" />
        <span class="text-brand-700">Webizon</span>
      </div>

      <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-4 scroll-thin">
        <NuxtLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
          active-class="bg-brand-50 text-brand-700 hover:bg-brand-50 hover:text-brand-700"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" />
          <span>{{ item.label }}</span>
        </NuxtLink>
      </nav>

      <div class="border-t border-slate-100 p-3">
        <NuxtLink
          to="/admin/events/create"
          class="flex items-center justify-center gap-2 rounded-lg bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          <Megaphone class="h-4 w-4" />
          Жаңа ивент
        </NuxtLink>
      </div>
    </aside>

    <!-- ─── Sidebar (mobile slide-over) ───────────────────────────────────── -->
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
        class="fixed inset-0 z-40 bg-slate-900/40 lg:hidden"
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
        class="fixed inset-y-0 left-0 z-50 w-72 border-r border-slate-200 bg-white lg:hidden"
      >
        <div class="flex h-16 items-center justify-between gap-2 border-b border-slate-100 px-5">
          <span class="flex items-center gap-2 font-semibold text-brand-700">
            <span class="inline-block h-3 w-3 rounded-full bg-brand-600" />
            Webizon
          </span>
          <button
            class="rounded-md p-1 text-slate-500 hover:bg-slate-100"
            @click="mobileOpen = false"
          >
            <X class="h-5 w-5" />
          </button>
        </div>
        <nav class="space-y-1 px-3 py-4">
          <NuxtLink
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            active-class="bg-brand-50 text-brand-700"
            @click="mobileOpen = false"
          >
            <component :is="item.icon" class="h-4 w-4" />
            {{ item.label }}
          </NuxtLink>
        </nav>
      </aside>
    </Transition>

    <!-- ─── Main column ──────────────────────────────────────────────────── -->
    <div class="lg:pl-60">
      <header
        class="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white/80 px-4 backdrop-blur lg:px-8"
      >
        <button
          class="rounded-md p-1 text-slate-500 hover:bg-slate-100 lg:hidden"
          aria-label="Open menu"
          @click="mobileOpen = true"
        >
          <Menu class="h-5 w-5" />
        </button>

        <div class="hidden items-center gap-2 text-sm text-slate-500 lg:flex">
          <span class="font-medium text-slate-900">{{ tenantLabel }}</span>
        </div>

        <HMenu as="div" class="relative ml-auto">
          <MenuButton
            class="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50"
          >
            <span
              class="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700"
            >
              {{ (displayName[0] ?? '?').toUpperCase() }}
            </span>
            <span class="hidden max-w-[12rem] truncate sm:inline">{{ displayName }}</span>
            <ChevronDown class="h-4 w-4 text-slate-400" />
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
              class="absolute right-0 mt-2 w-56 origin-top-right rounded-xl border border-slate-200 bg-white py-1 shadow-overlay focus:outline-none"
            >
              <div class="border-b border-slate-100 px-4 py-2">
                <p class="text-sm font-semibold text-slate-900">{{ displayName }}</p>
                <p class="truncate text-xs text-slate-500">{{ auth.user?.email }}</p>
              </div>
              <MenuItem v-slot="{ active }">
                <button
                  :class="[
                    active ? 'bg-slate-50' : '',
                    'flex w-full items-center gap-2 px-4 py-2 text-sm text-slate-700',
                  ]"
                  @click="logout"
                >
                  <LogOut class="h-4 w-4" />
                  Шығу
                </button>
              </MenuItem>
            </MenuItems>
          </Transition>
        </HMenu>
      </header>

      <main class="mx-auto w-full max-w-7xl px-4 py-6 lg:px-8 lg:py-10">
        <slot />
      </main>
    </div>
  </div>
</template>
