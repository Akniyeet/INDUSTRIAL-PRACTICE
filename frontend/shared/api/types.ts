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
// Room bootstrap
// ---------------------------------------------------------------------------

export interface RoomChatSettingsView {
  allowLinks: boolean
  slowModeSeconds: number
  showParticipantCount: boolean
  showParticipantNames: boolean
  welcomeMessage: string | null
}

export interface RoomChatMessageView {
  id: UUID
  userId: UUID | null
  displayName: string | null
  type: MessageType
  replyToMessageId: UUID | null
  text: string
  offsetSeconds: number | null
  createdAt: ISODate
}

export interface RoomChannelBundleView {
  chatChannel: string
  timelineChannel: string
  systemChannel: string
  presenceChannel: string
}

export interface RoomCapabilitiesView {
  canSendMessages: boolean
  canModerate: boolean
  canTriggerCta: boolean
  canBroadcastSystem: boolean
  isMuted: boolean
  muteUntil: ISODate | null
  slowModeSeconds: number
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
