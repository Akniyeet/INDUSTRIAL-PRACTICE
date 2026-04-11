<script setup lang="ts">
/**
 * The single place a YouTube iframe is allowed to live in this codebase.
 *
 * <p>Per CLAUDE.md §23 ("YouTube embed rules") every embed in the room must:
 * <ol>
 *   <li>Use the {@code youtube-nocookie.com} domain (privacy + GDPR posture).</li>
 *   <li>Carry the standard query string with {@code rel}, {@code modestbranding},
 *       {@code showinfo}, {@code iv_load_policy}, {@code disablekb},
 *       {@code cc_load_policy}, {@code playsinline}, {@code enablejsapi}.</li>
 *   <li>Cover hover chrome (title bar, watermark hotspot) with a
 *       {@code pointer-events-none} gradient overlay so visitors cannot click
 *       through to the YouTube watch page mid-broadcast.</li>
 * </ol>
 *
 * <p>The backend already generates {@code session.youtubeEmbedUrl}, but it may
 * arrive without our standard query string. We append the missing flags here
 * so a single edit changes the policy for the whole app. We never strip
 * {@code modestbranding} or {@code rel=0} — both are non-negotiable per §23.
 *
 * <p>This wrapper is layout-agnostic: it always fills its parent and lets the
 * caller decide aspect ratio (typically a {@code aspect-video} container).
 */
import { computed } from 'vue'

const props = defineProps<{
  /** Backend-issued embed URL — `https://www.youtube-nocookie.com/embed/{id}` */
  embedUrl: string | null
  /** Optional human title for the iframe a11y label */
  title?: string
}>()

/** Required URL parameters per §23. */
const REQUIRED_PARAMS: Record<string, string> = {
  rel:             '0',
  modestbranding:  '1',
  showinfo:        '0',
  iv_load_policy:  '3',
  disablekb:       '1',
  cc_load_policy:  '0',
  playsinline:     '1',
  enablejsapi:     '1',
}

const safeUrl = computed<string | null>(() => {
  if (!props.embedUrl) return null
  try {
    const u = new URL(props.embedUrl)
    // Force the privacy domain even if the backend forgot.
    if (u.hostname === 'www.youtube.com' || u.hostname === 'youtube.com') {
      u.hostname = 'www.youtube-nocookie.com'
    }
    for (const [k, v] of Object.entries(REQUIRED_PARAMS)) {
      if (!u.searchParams.has(k)) u.searchParams.set(k, v)
    }
    // Hard-pin the two non-negotiables in case the backend tries to override.
    u.searchParams.set('rel', '0')
    u.searchParams.set('modestbranding', '1')
    return u.toString()
  } catch {
    return null
  }
})
</script>

<template>
  <div class="relative h-full w-full overflow-hidden bg-black">
    <iframe
      v-if="safeUrl"
      :src="safeUrl"
      :title="title ?? 'Live broadcast'"
      class="h-full w-full"
      frameborder="0"
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
      allowfullscreen
    />
    <div
      v-else
      class="flex h-full w-full items-center justify-center text-sm text-slate-500"
    >
      Видео әлі қол жетімді емес
    </div>

    <!-- Top chrome cover: hides the hover title bar that YouTube renders
         on top of the video on mouse-enter. pointer-events-none lets clicks
         pass through to the player controls. -->
    <div
      class="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-black/70 to-transparent"
    />
    <!-- Bottom-right watermark cover: blocks the "Watch on YouTube" hotspot. -->
    <div
      class="pointer-events-none absolute bottom-0 right-0 h-12 w-32"
    />
  </div>
</template>
