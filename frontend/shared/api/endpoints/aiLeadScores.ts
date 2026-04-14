import type { ApiClient } from '../client'

export type LeadClassification = 'HOT' | 'WARM' | 'COLD'

export interface AiLeadScoreResponse {
  id: string
  eventId: string
  sessionId: string
  profileId: string
  ruleScore: number
  classification: LeadClassification
  confidence: number
  reasoning: string | null
  recommendedAction: string | null
  followUpHours: number | null
  aiModel: string | null
  watchDurationSeconds: number
  watchPercent: number
  chatMessagesCount: number
  ctaClicksCount: number
  ctaDetails: Record<string, unknown>[]
  returnedForAuto: boolean
  createdAt: string
}

export interface SessionLeadSummaryResponse {
  total: number
  hot: number
  warm: number
  cold: number
}

export class AiLeadScoresApi {
  constructor(private readonly client: ApiClient) {}

  /** Get all lead scores for a session */
  getSessionScores(sessionId: string, classification?: LeadClassification) {
    return this.client.get<AiLeadScoreResponse[]>(
      `/v1/ai/lead-scores/sessions/${sessionId}`,
      { query: classification ? { classification } : undefined },
    )
  }

  /** Get lead summary stats */
  getSessionSummary(sessionId: string) {
    return this.client.get<SessionLeadSummaryResponse>(
      `/v1/ai/lead-scores/sessions/${sessionId}/summary`,
    )
  }

  /** Trigger AI scoring for all attendees */
  scoreSession(sessionId: string) {
    return this.client.post<AiLeadScoreResponse[]>(
      `/v1/ai/lead-scores/sessions/${sessionId}/score`,
    )
  }

  /** Score a single attendee */
  scoreAttendee(sessionId: string, profileId: string) {
    return this.client.post<AiLeadScoreResponse>(
      `/v1/ai/lead-scores/sessions/${sessionId}/profiles/${profileId}/score`,
    )
  }

  /** Lead history for a user */
  getProfileHistory(profileId: string) {
    return this.client.get<AiLeadScoreResponse[]>(
      `/v1/ai/lead-scores/profiles/${profileId}`,
    )
  }
}
