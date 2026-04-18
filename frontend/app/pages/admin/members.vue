<script setup lang="ts">
/**
 * Tenant member management at {@code /admin/members}.
 *
 * <p>Two sections:
 * <ol>
 *   <li>Active members — lists all ACTIVE tenant users with role badges</li>
 *   <li>Invitations — lists pending/recent invites with revoke actions</li>
 * </ol>
 *
 * <p>Admins can invite new members via email, assigning a role. The invite
 * token is returned once and can be copied to share with the invitee.
 */
import type {
  MembershipResponse,
  MembershipRole,
  InviteResponse,
  InviteStatus,
} from '#shared/api/types'
import {
  Users,
  UserPlus,
  RefreshCw,
  Mail,
  Shield,
  ShieldCheck,
  Eye as EyeIcon,
  Megaphone,
  BarChart3,
  XCircle,
  Clock,
  CheckCircle,
  Copy,
  AlertTriangle,
} from 'lucide-vue-next'
import { format, formatDistanceToNow } from 'date-fns'
import { ref, computed } from 'vue'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Участники — Webizon' })

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Members
// ---------------------------------------------------------------------------

const members = ref<MembershipResponse[]>([])
const membersLoading = ref(false)

async function loadMembers() {
  membersLoading.value = true
  try {
    members.value = await api.auth.myMemberships()
  } catch {
    toast.error('Ошибка загрузки участников')
  } finally {
    membersLoading.value = false
  }
}

onMounted(loadMembers)

// ---------------------------------------------------------------------------
// Invites
// ---------------------------------------------------------------------------

const invites = ref<InviteResponse[]>([])
const invitesLoading = ref(false)
const inviteFilter = ref<InviteStatus | ''>('')

async function loadInvites() {
  invitesLoading.value = true
  try {
    const params: { status?: InviteStatus } = {}
    if (inviteFilter.value) params.status = inviteFilter.value
    const page = await api.invites.list(params)
    invites.value = page.content
  } catch {
    toast.error('Ошибка загрузки приглашений')
  } finally {
    invitesLoading.value = false
  }
}

onMounted(loadInvites)

function refreshAll() {
  loadMembers()
  loadInvites()
}

// ---------------------------------------------------------------------------
// Create invite modal
// ---------------------------------------------------------------------------

const inviteModalOpen = ref(false)
const inviteForm = ref({
  email: '',
  role: 'TENANT_MODERATOR' as MembershipRole,
  message: '',
})
const creating = ref(false)
const createdAcceptUrl = ref<string | null>(null)

function openInvite() {
  inviteForm.value = { email: '', role: 'TENANT_MODERATOR', message: '' }
  createdAcceptUrl.value = null
  inviteModalOpen.value = true
}

async function submitInvite() {
  creating.value = true
  try {
    const result = await api.invites.create({
      email: inviteForm.value.email,
      role: inviteForm.value.role,
      message: inviteForm.value.message || undefined,
    })
    createdAcceptUrl.value = result.acceptUrl
    toast.success(`Приглашение отправлено: ${inviteForm.value.email}`)
    loadInvites()
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    toast.error(apiErr.detail ?? 'Ошибка отправки приглашения')
  } finally {
    creating.value = false
  }
}

async function copyUrl() {
  if (!createdAcceptUrl.value) return
  try {
    await navigator.clipboard.writeText(createdAcceptUrl.value)
    toast.success('Ссылка скопирована')
  } catch {
    toast.error('Не удалось скопировать')
  }
}

// ---------------------------------------------------------------------------
// Revoke invite
// ---------------------------------------------------------------------------

const revokeModalOpen = ref(false)
const pendingRevoke = ref<InviteResponse | null>(null)
const revoking = ref(false)

function askRevoke(invite: InviteResponse) {
  pendingRevoke.value = invite
  revokeModalOpen.value = true
}

async function confirmRevoke() {
  if (!pendingRevoke.value) return
  revoking.value = true
  try {
    await api.invites.revoke(pendingRevoke.value.id)
    toast.success('Приглашение отозвано')
    revokeModalOpen.value = false
    pendingRevoke.value = null
    loadInvites()
  } catch {
    toast.error('Ошибка отзыва')
  } finally {
    revoking.value = false
  }
}

// ---------------------------------------------------------------------------
// Role helpers
// ---------------------------------------------------------------------------

const ROLE_LABELS: Record<MembershipRole, string> = {
  TENANT_OWNER: 'Владелец',
  TENANT_ADMIN: 'Админ',
  TENANT_MODERATOR: 'Модератор',
  TENANT_PRESENTER: 'Спикер',
  TENANT_ANALYST: 'Аналитик',
}

const ROLE_COLORS: Record<MembershipRole, string> = {
  TENANT_OWNER: 'bg-brand-100 text-brand-700',
  TENANT_ADMIN: 'bg-danger-100 text-danger-700',
  TENANT_MODERATOR: 'bg-warning-100 text-warning-700',
  TENANT_PRESENTER: 'bg-success-100 text-success-700',
  TENANT_ANALYST: 'bg-violet-100 text-violet-700',
}

const ROLE_ICONS: Record<MembershipRole, typeof Shield> = {
  TENANT_OWNER: ShieldCheck,
  TENANT_ADMIN: Shield,
  TENANT_MODERATOR: EyeIcon,
  TENANT_PRESENTER: Megaphone,
  TENANT_ANALYST: BarChart3,
}

const ASSIGNABLE_ROLES: MembershipRole[] = [
  'TENANT_ADMIN',
  'TENANT_MODERATOR',
  'TENANT_PRESENTER',
  'TENANT_ANALYST',
]

const INVITE_STATUS_LABELS: Record<InviteStatus, string> = {
  PENDING: 'Ожидает',
  ACCEPTED: 'Принято',
  REVOKED: 'Отозвано',
  EXPIRED: 'Истекло',
}

const INVITE_STATUS_COLORS: Record<InviteStatus, string> = {
  PENDING: 'bg-warning-100 text-warning-700',
  ACCEPTED: 'bg-success-100 text-success-700',
  REVOKED: 'bg-slate-100 text-slate-500',
  EXPIRED: 'bg-slate-100 text-slate-400',
}

const INVITE_STATUS_ICONS: Record<InviteStatus, typeof Clock> = {
  PENDING: Clock,
  ACCEPTED: CheckCircle,
  REVOKED: XCircle,
  EXPIRED: Clock,
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function formatDate(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

function formatRelative(iso: string) {
  try { return formatDistanceToNow(new Date(iso), { addSuffix: true }) } catch { return '' }
}
</script>

<template>
  <div>
    <PageHeader
      title="Участники"
      subtitle="Управление участниками команды и отправка приглашений"
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Участники' },
      ]"
    >
      <template #actions>
        <UiButton variant="outline" size="md" :disabled="membersLoading || invitesLoading" @click="refreshAll">
          <RefreshCw class="h-4 w-4" :class="(membersLoading || invitesLoading) && 'animate-spin'" />
          Обновить
        </UiButton>
        <UiButton variant="primary" size="md" @click="openInvite">
          <UserPlus class="h-4 w-4" />
          Пригласить
        </UiButton>
      </template>
    </PageHeader>

    <!-- ──── Active Members ─────────────────────────────────────────── -->
    <section class="mt-6">
      <div class="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        <Users class="h-4 w-4" />
        Активные участники ({{ members.length }})
      </div>

      <div v-if="membersLoading && members.length === 0" class="space-y-2">
        <UiSkeleton v-for="i in 3" :key="i" h="h-[60px]" rounded="rounded-xl" />
      </div>

      <UiEmpty
        v-else-if="members.length === 0"
        title="Нет участников"
        description="Пригласите участников в вашу команду."
      >
        <template #icon><Users class="h-5 w-5" /></template>
      </UiEmpty>

      <UiCard v-else :padded="false">
        <ul class="divide-y divide-slate-100">
          <li
            v-for="m in members"
            :key="m.id"
            class="flex items-center justify-between px-5 py-3.5"
          >
            <div class="flex items-center gap-3">
              <div
                class="flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-sm font-semibold text-slate-600"
              >
                {{ m.userId.slice(0, 1).toUpperCase() }}
              </div>
              <div>
                <p class="text-sm font-medium text-slate-900">
                  {{ m.userId.slice(0, 8) }}…
                </p>
                <p class="text-xs text-slate-500">
                  {{ m.tenantDisplayName }} · {{ formatRelative(m.joinedAt) }}
                </p>
              </div>
            </div>
            <span
              class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
              :class="ROLE_COLORS[m.role]"
            >
              <component :is="ROLE_ICONS[m.role]" class="h-3.5 w-3.5" />
              {{ ROLE_LABELS[m.role] }}
            </span>
          </li>
        </ul>
      </UiCard>
    </section>

    <!-- ──── Invitations ────────────────────────────────────────────── -->
    <section class="mt-8">
      <div class="mb-3 flex items-center justify-between">
        <div class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          <Mail class="h-4 w-4" />
          Приглашения
        </div>
        <!-- Filter -->
        <div class="flex items-center gap-1 rounded-lg bg-slate-100 p-0.5">
          <button
            v-for="opt in [
              { value: '', label: 'Все' },
              { value: 'PENDING', label: 'Ожидает' },
              { value: 'ACCEPTED', label: 'Принято' },
            ] as const"
            :key="opt.value"
            class="rounded-md px-2.5 py-1 text-xs font-medium transition-colors"
            :class="inviteFilter === opt.value
              ? 'bg-white text-slate-900 shadow-sm'
              : 'text-slate-500 hover:text-slate-700'"
            @click="inviteFilter = opt.value; loadInvites()"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <div v-if="invitesLoading && invites.length === 0" class="space-y-2">
        <UiSkeleton v-for="i in 3" :key="i" h="h-[56px]" rounded="rounded-xl" />
      </div>

      <UiEmpty
        v-else-if="invites.length === 0"
        title="Нет приглашений"
        description="Нажмите кнопку «Пригласить», чтобы отправить приглашение новому участнику."
      >
        <template #icon><Mail class="h-5 w-5" /></template>
      </UiEmpty>

      <UiCard v-else :padded="false">
        <ul class="divide-y divide-slate-100">
          <li
            v-for="inv in invites"
            :key="inv.id"
            class="flex items-center justify-between px-5 py-3"
          >
            <div class="flex items-center gap-3 min-w-0">
              <div
                class="flex h-8 w-8 items-center justify-center rounded-full text-sm"
                :class="INVITE_STATUS_COLORS[inv.status]"
              >
                <component :is="INVITE_STATUS_ICONS[inv.status]" class="h-4 w-4" />
              </div>
              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-slate-900">{{ inv.email }}</p>
                <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-slate-500">
                  <span
                    class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                    :class="ROLE_COLORS[inv.role]"
                  >
                    {{ ROLE_LABELS[inv.role] }}
                  </span>
                  <span>{{ formatDate(inv.createdAt) }}</span>
                  <span v-if="inv.status === 'PENDING'" class="text-warning-600">
                    Истекает: {{ formatDate(inv.expiresAt) }}
                  </span>
                </div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span
                class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                :class="INVITE_STATUS_COLORS[inv.status]"
              >
                {{ INVITE_STATUS_LABELS[inv.status] }}
              </span>
              <UiButton
                v-if="inv.status === 'PENDING'"
                variant="ghost"
                size="sm"
                @click="askRevoke(inv)"
              >
                <XCircle class="h-3.5 w-3.5" />
                Отозвать
              </UiButton>
            </div>
          </li>
        </ul>
      </UiCard>
    </section>

    <!-- ──── Invite Modal ──────────────────────────────────────────── -->
    <UiModal v-model="inviteModalOpen" :title="createdAcceptUrl ? 'Приглашение отправлено' : 'Новое приглашение'" size="md">
      <!-- Success state: show accept URL -->
      <template v-if="createdAcceptUrl">
        <div class="space-y-4">
          <div class="rounded-lg border border-success-200 bg-success-50 p-4">
            <p class="text-sm font-medium text-success-800">
              Приглашение успешно отправлено!
            </p>
            <p class="mt-1 text-xs text-success-700">
              Отправьте ссылку ниже приглашённому. Ссылка отображается только один раз.
            </p>
          </div>
          <div class="flex items-center gap-2">
            <input
              :value="createdAcceptUrl"
              readonly
              class="flex-1 rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 font-mono text-xs text-slate-700"
            />
            <UiButton variant="outline" size="sm" @click="copyUrl">
              <Copy class="h-4 w-4" />
            </UiButton>
          </div>
        </div>
      </template>

      <!-- Create form -->
      <template v-else>
        <div class="space-y-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Email</label>
            <UiInput
              v-model="inviteForm.email"
              type="email"
              placeholder="user@example.com"
            />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Роль</label>
            <UiSelect v-model="inviteForm.role">
              <option v-for="r in ASSIGNABLE_ROLES" :key="r" :value="r">
                {{ ROLE_LABELS[r] }}
              </option>
            </UiSelect>
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Сообщение (необязательно)</label>
            <textarea
              v-model="inviteForm.message"
              rows="2"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
              placeholder="Приглашаем присоединиться к команде..."
            />
          </div>
        </div>
      </template>

      <template #footer>
        <UiButton variant="outline" @click="inviteModalOpen = false">
          {{ createdAcceptUrl ? 'Закрыть' : 'Отмена' }}
        </UiButton>
        <UiButton
          v-if="!createdAcceptUrl"
          variant="primary"
          :loading="creating"
          :disabled="!inviteForm.email"
          @click="submitInvite"
        >
          <Mail class="h-4 w-4" />
          Отправить
        </UiButton>
      </template>
    </UiModal>

    <!-- ──── Revoke Confirm ────────────────────────────────────────── -->
    <UiModal v-model="revokeModalOpen" title="Отозвать приглашение" size="sm">
      <div class="flex items-start gap-3 rounded-lg border border-warning-200 bg-warning-50 p-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-warning-500" />
        <p class="text-sm text-warning-800">
          Приглашение на адрес {{ pendingRevoke?.email }} будет отозвано.
          Этот человек не сможет войти по ссылке.
        </p>
      </div>
      <template #footer>
        <UiButton variant="outline" :disabled="revoking" @click="revokeModalOpen = false">
          Закрыть
        </UiButton>
        <UiButton variant="danger" :loading="revoking" @click="confirmRevoke">
          <XCircle class="h-4 w-4" />
          Отозвать
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
