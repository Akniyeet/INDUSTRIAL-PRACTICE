/**
 * Shared TypeScript contracts mirroring backend DTOs.
 *
 * These types are written by hand (not codegen) because:
 *   1. We want explicit JSDoc on every field — the frontend is the only
 *      place that looks at these payloads, so the comments double as docs.
 *   2. The backend uses Java `Instant` for timestamps, serialised as ISO-8601
 *      strings. We keep them as `string` in TS and convert at the edge via
 *      `date-fns`, so the narrowing happens at one place (view layer).
 *   3. Enum values are mirrored exactly as Java enum names (UPPER_SNAKE).
 *
 * If a new backend DTO is added, mirror it here and then add a function
 * in the corresponding `endpoints/*.ts` module that consumes it.
 */

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

export type UUID = string
export type ISODate = string

/** Java Spring Data page envelope */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

// ---------------------------------------------------------------------------
// Tenancy
// ---------------------------------------------------------------------------

export type MembershipRole =
  | 'TENANT_OWNER'
  | 'TENANT_ADMIN'
  | 'TENANT_MODERATOR'
  | 'TENANT_PRESENTER'
  | 'TENANT_ANALYST'

export type MembershipStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED' | 'REMOVED'

export interface TenantSummary {
  id: UUID
  slug: string
  displayName: string
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED'
}

export interface MembershipResponse {
  id: UUID
  tenantId: UUID
  tenantSlug: string
  tenantDisplayName: string
  userId: UUID
  role: MembershipRole
  status: MembershipStatus
  joinedAt: ISODate
}

export interface UserResponse {
  id: UUID
  keycloakId: string
  email: string
  fullName: string | null
  avatarUrl: string | null
  createdAt: ISODate
}

export interface BootstrapResponse {
  user: UserResponse
  memberships: MembershipResponse[]
}

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface EventResponse {
  id: UUID
  slug: string
  title: string
  description: string | null
  speakerName: string | null
  speakerBio: string | null
  coverImageUrl: string | null
  timezone: string | null
  language: string | null
  status: EventStatus
  createdByUserId: UUID
  createdAt: ISODate
  updatedAt: ISODate
  version: number
}

export interface EventCreateRequest {
  slug: string
  title: string
  description?: string
  speakerName?: string
  speakerBio?: string
  coverImageUrl?: string
  timezone?: string
  language?: string
}

export type EventUpdateRequest = Partial<Omit<EventCreateRequest, 'slug'>>

// ---------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------

export type SessionType = 'LIVE' | 'AUTO'

export type SessionStatus =
  | 'SCHEDULED'
  | 'LIVE'
  | 'ENDED'
  | 'CANCELLED'
  | 'AUTO_SCHEDULED'
  | 'AUTO_LIVE'
  | 'AUTO_ENDED'

export interface SessionResponse {
  id: UUID
  eventId: UUID
  type: SessionType
  status: SessionStatus
  startTime: ISODate
  plannedDurationSeconds: number
  youtubeUrl: string | null
  youtubeVideoId: string | null
  youtubeEmbedUrl: string | null
  sourceLiveSessionId: UUID | null
  actualStartedAt: ISODate | null
  actualEndedAt: ISODate | null
  finalizedAt: ISODate | null
  createdByUserId: UUID
  createdAt: ISODate
  updatedAt: ISODate
  version: number
}

export interface SessionCreateRequest {
  type: SessionType
  startTime: ISODate
  plannedDurationSeconds: number
  youtubeUrl?: string
  sourceLiveSessionId?: UUID
}

export type SessionUpdateRequest = Partial<Omit<SessionCreateRequest, 'type'>>

// ---------------------------------------------------------------------------
// Public event resolution
// ---------------------------------------------------------------------------

export type PublicEventState =
  | 'LIVE_NOW'
  | 'WAITING'
  | 'SLOT_SELECTION'
  | 'LANDING'
  | 'UNAVAILABLE'

export interface PublicEventView {
  id: UUID
  slug: string
  title: string
  description: string | null
  speakerName: string | null
  speakerBio: string | null
  coverImageUrl: string | null
  timezone: string | null
  language: string | null
}

export interface PublicSessionView {
  id: UUID
  type: SessionType
  status: SessionStatus
  startTime: ISODate
  plannedDurationSeconds: number
  youtubeEmbedUrl: string | null
}

export interface PublicEventResolution {
  state: PublicEventState
  event: PublicEventView | null
  activeSession: PublicSessionView | null
  nextSession: PublicSessionView | null
  autoSlots: PublicSessionView[]
}

// ---------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------

export type MessageType = 'USER' | 'ADMIN' | 'SYSTEM' | 'HISTORICAL'

export interface ChatMessageResponse {
  id: UUID
  sessionId: UUID
  userId: UUID | null
  type: MessageType
  replyToMessageId: UUID | null
  text: string
  offsetSeconds: number | null
  createdAt: ISODate
}

export interface SendMessageRequest {
  text: string
  replyToMessageId?: UUID
}

export interface ChatSettingsResponse {
  eventId: UUID
  allowLinks: boolean
  slowModeSeconds: number
  showParticipantCount: boolean
  showParticipantNames: boolean
  welcomeMessage: string | null
  premoderationEnabled: boolean
  profanityFilterEnabled: boolean
  antiSpamEnabled: boolean
}

// ---------------------------------------------------------------------------
// CTA
// ---------------------------------------------------------------------------

export type CtaType = 'FILE' | 'LINK' | 'COURSE' | 'FORM'
export type CtaPlacement = 'INLINE' | 'SIDEBAR' | 'POPUP' | 'BELOW_VIDEO'

export interface CtaResponse {
  id: UUID
  eventId: UUID
  title: string
  description: string | null
  type: CtaType
  buttonText: string
  actionUrl: string | null
  fileUrl: string | null
  placement: CtaPlacement
  priority: number
  allowStack: boolean
  active: boolean
  createdAt: ISODate
  updatedAt: ISODate
}

export interface CtaCreateRequest {
  title: string
  description?: string
  type: CtaType
  buttonText: string
  actionUrl?: string
  fileUrl?: string
  placement: CtaPlacement
  priority?: number
  allowStack?: boolean
}

export type CtaUpdateRequest = Partial<CtaCreateRequest>

// ---------------------------------------------------------------------------
// Moderation
// ---------------------------------------------------------------------------

/**
 * All moderator-grade actions recorded in the audit log.
 *
 * <p>The backend enum lives at {@code com.webizon.chat.model.ModerationActionType}
 * — keep this in sync. UI uses it to label rows and to decide which icon /
 * colour to show in the moderation log.
 */
export type ModerationActionType =
  | 'WARNING'
  | 'MUTE'
  | 'CHAT_BAN'
  | 'ROOM_REMOVE'
  | 'FULL_BAN'
  | 'MESSAGE_DELETE'
  | 'MESSAGE_HIDE'

export interface ModerationActionResponse {
  id: UUID
  sessionId: UUID
  targetUserId: UUID
  moderatorUserId: UUID
  actionType: ModerationActionType
  targetMessageId: UUID | null
  reason: string | null
  durationSeconds: number | null
  createdAt: ISODate
}

export interface WarnRequest {
  targetUserId: UUID
  reason?: string
}

export interface MuteRequest {
  targetUserId: UUID
  /** 1..86400. Backend enforces the clamp — UI should still send sane defaults. */
  durationSeconds: number
  reason?: string
}

export interface BanRequest {
  targetUserId: UUID
  reason?: string
}

export interface DeleteMessageRequest {
  messageId: UUID
  reason?: string
}

export interface HideMessageRequest {
  messageId: UUID
  reason?: string
}

// ---------------------------------------------------------------------------
// Room bootstrap
// ---------------------------------------------------------------------------

export interface RoomChatSettingsView {
  allowLinks: boolean
  slowModeSeconds: number
  showParticipantCount: boolean
  showParticipantNames: boolean
  welcomeMessage: string | null
}

/**
 * Room-bootstrap projection of a chat row.
 *
 * <p>Shape matches {@code com.webizon.room.api.dto.RoomChatMessageView} on
 * the backend exactly. Notably {@code messageType} (not {@code type}) and
 * there is no display name — the UI currently falls back to a short form
 * of the user id for the avatar initial. Backend enrichment with a
 * resolved display name is tracked as a future backlog item; until then
 * the UI stays honest about what it actually has.
 */
export interface RoomChatMessageView {
  id: UUID
  userId: UUID | null
  messageType: MessageType
  replyToMessageId: UUID | null
  text: string
  offsetSeconds: number | null
  createdAt: ISODate
}

/**
 * Centrifugo channel bundle returned in the bootstrap payload.
 *
 * <p>Backend builds these via {@code ChannelNameFactory.sessionChannel} in
 * the canonical form {@code tenant.{tenantId}.session.{sessionId}.{kind}}
 * with {@code kind} in (chat, cta, presence, state). The admin-only
 * {@code control} kind is NOT exposed through this bundle — moderators
 * construct it from the session id on their own if needed.
 */
export interface RoomChannelBundleView {
  chat: string
  cta: string
  presence: string
  state: string
}

/**
 * Role-derived capability hints for the room UI.
 *
 * <p>These are UI-only — every action is re-checked server-side via
 * {@code @PreAuthorize}. Shape matches {@code RoomCapabilitiesView} on the
 * backend: {@code canSendChat}, {@code canReplyInChat}, {@code canModerate},
 * {@code canTriggerCtas}, {@code bypassSlowMode}. Slow-mode window itself
 * lives on {@link RoomChatSettingsView} so it can be changed per event
 * without a capability recompute.
 */
export interface RoomCapabilitiesView {
  canSendChat: boolean
  canReplyInChat: boolean
  canModerate: boolean
  canTriggerCtas: boolean
  bypassSlowMode: boolean
}

export interface RoomBootstrapResponse {
  event: EventResponse
  session: SessionResponse
  chatSettings: RoomChatSettingsView
  recentChat: RoomChatMessageView[]
  activeCtas: CtaResponse[]
  presentNowCount: number
  currentOffsetSeconds: number | null
  channels: RoomChannelBundleView
  capabilities: RoomCapabilitiesView
}

// ---------------------------------------------------------------------------
// Realtime token
// ---------------------------------------------------------------------------

export interface RealtimeTokenResponse {
  token: string
  expiresAt: ISODate
}

export interface RealtimeSubscribeTokenResponse {
  channel: string
  token: string
  expiresAt: ISODate
}

// ---------------------------------------------------------------------------
// Error envelope
// ---------------------------------------------------------------------------

/**
 * Spring `ProblemDetail` shape (RFC 7807). All backend errors bubble up in
 * this form; the frontend's error handler normalises ofetch's native error
 * into this before surfacing it to user-facing code.
 */
export interface ApiError {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** Spring adds a `timestamp` for most errors */
  timestamp?: string
  /** Validation errors include per-field messages */
  errors?: Record<string, string>
}
