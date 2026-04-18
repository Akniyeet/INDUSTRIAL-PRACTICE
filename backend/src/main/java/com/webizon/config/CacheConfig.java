package com.webizon.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures the app-local Caffeine cache manager used for read-mostly
 * domain state on the room hot path.
 *
 * <h2>What lives in cache</h2>
 * <ul>
 *   <li>{@code chatSettings} — one {@code EventChatSettings} per event.
 *       Admin edits are rare; every chat send reads it. Short TTL so a
 *       fresh setting flips in without a restart if the CacheEvict miss
 *       fires on an instance that didn't own the write.</li>
 *   <li>{@code activeCtas} — list of currently-active CTAs per event.
 *       Room bootstrap refreshes this; admins toggle via explicit
 *       eviction. Time-bounded as a safety net.</li>
 * </ul>
 *
 * <h2>Why not Redis</h2>
 * These caches are idempotent (re-reading the DB on miss is always
 * correct) and the win is avoiding a DB round-trip, not coordination.
 * Redis would add network hops and cross-instance staleness windows —
 * Caffeine's in-JVM storage is strictly faster and simpler. When we
 * scale out to multiple backend pods the per-pod cache will see a
 * slightly staler view of admin edits (bounded by the TTL), which is
 * acceptable for both caches.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_CHAT_SETTINGS = "chatSettings";
    public static final String CACHE_ACTIVE_CTAS   = "activeCtas";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_CHAT_SETTINGS,
                CACHE_ACTIVE_CTAS);
        manager.setCaffeine(Caffeine.newBuilder()
                // 10k events per pod is plenty — the working set is
                // usually "events running right now" which is <1k.
                .maximumSize(10_000)
                // 60s TTL is a compromise: short enough that a
                // forgotten CacheEvict doesn't leave UI stale for long,
                // long enough to soak the chat-send hot path.
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats());
        return manager;
    }
}
