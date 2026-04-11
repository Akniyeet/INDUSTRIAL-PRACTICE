package com.webizon.events.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable value object that validates and normalises a YouTube video URL.
 *
 * <p>The only video provider Webizon supports is YouTube (see CLAUDE.md
 * §13 and the "CRITICAL ARCHITECTURE RULES" block). Admins typically paste
 * any of the public share formats:
 *
 * <ul>
 *   <li>{@code https://www.youtube.com/watch?v=VIDEOID}</li>
 *   <li>{@code https://youtu.be/VIDEOID}</li>
 *   <li>{@code https://www.youtube.com/live/VIDEOID}</li>
 *   <li>{@code https://www.youtube.com/embed/VIDEOID}</li>
 *   <li>{@code https://www.youtube.com/shorts/VIDEOID}</li>
 * </ul>
 *
 * <p>The parser extracts the canonical 11-character video id, rejects any
 * other provider (Vimeo, Rutube, Twitch, ...), and produces a safe embed URL
 * that can be dropped into an iframe.
 */
public final class YouTubeUrl {

    /** YouTube video ids are 11 chars of [A-Za-z0-9_-]. */
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private static final String EMBED_TEMPLATE = "https://www.youtube.com/embed/%s";

    private final String videoId;
    private final String originalUrl;
    private final String embedUrl;

    private YouTubeUrl(String videoId, String originalUrl) {
        this.videoId = videoId;
        this.originalUrl = originalUrl;
        this.embedUrl = EMBED_TEMPLATE.formatted(videoId);
    }

    public String videoId() {
        return videoId;
    }

    public String originalUrl() {
        return originalUrl;
    }

    public String embedUrl() {
        return embedUrl;
    }

    /**
     * Parse a user-supplied YouTube URL.
     *
     * @param input the raw string the admin pasted into the form
     * @return a validated {@code YouTubeUrl}
     * @throws IllegalArgumentException if the input is null, blank, or not a
     *                                  recognizable YouTube video URL
     */
    public static YouTubeUrl parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("YouTube URL must not be blank");
        }
        String trimmed = input.trim();

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid URL: " + trimmed);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("URL must include a host: " + trimmed);
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.startsWith("www.")) {
            lowerHost = lowerHost.substring(4);
        }
        if (lowerHost.startsWith("m.")) {
            lowerHost = lowerHost.substring(2);
        }

        String path = uri.getPath() == null ? "" : uri.getPath();

        String candidateId = switch (lowerHost) {
            case "youtu.be" -> stripLeadingSlash(path);
            case "youtube.com", "music.youtube.com" -> extractFromYoutubeCom(path, uri.getRawQuery());
            default -> throw new IllegalArgumentException(
                    "Only YouTube URLs are accepted (got host '" + host + "')");
        };

        if (candidateId == null || !VIDEO_ID_PATTERN.matcher(candidateId).matches()) {
            throw new IllegalArgumentException(
                    "Could not extract a valid YouTube video id from: " + trimmed);
        }
        return new YouTubeUrl(candidateId, trimmed);
    }

    private static String extractFromYoutubeCom(String path, String rawQuery) {
        // /watch?v=ID
        if (path.equals("/watch")) {
            return queryParam(rawQuery, "v");
        }
        // /embed/ID, /live/ID, /shorts/ID, /v/ID
        for (String prefix : new String[]{"/embed/", "/live/", "/shorts/", "/v/"}) {
            if (path.startsWith(prefix)) {
                return stripTrailingSegments(path.substring(prefix.length()));
            }
        }
        return null;
    }

    private static String stripLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return stripTrailingSegments(path.substring(1));
        }
        return null;
    }

    private static String stripTrailingSegments(String remainder) {
        int slash = remainder.indexOf('/');
        int q = remainder.indexOf('?');
        int end = remainder.length();
        if (slash >= 0) end = Math.min(end, slash);
        if (q >= 0) end = Math.min(end, q);
        return remainder.substring(0, end);
    }

    private static String queryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            if (pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YouTubeUrl other)) return false;
        return videoId.equals(other.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId);
    }

    @Override
    public String toString() {
        return "YouTubeUrl[" + videoId + "]";
    }

    /** For tests / debug: the map of recognized hosts → example format. */
    public static Map<String, String> supportedFormats() {
        return Map.of(
                "youtube.com", "https://www.youtube.com/watch?v=VIDEOID",
                "youtu.be", "https://youtu.be/VIDEOID",
                "youtube.com/live", "https://www.youtube.com/live/VIDEOID",
                "youtube.com/embed", "https://www.youtube.com/embed/VIDEOID",
                "youtube.com/shorts", "https://www.youtube.com/shorts/VIDEOID"
        );
    }
}
