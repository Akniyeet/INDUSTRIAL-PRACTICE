package com.webizon.chat;

import com.webizon.IntegrationTestBase;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.MessageType;
import com.webizon.chat.repo.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ChatMessageRepository}, particularly
 * the cursor-based (keyset) pagination queries.
 *
 * <p>Uses real PostgreSQL via Testcontainers to validate that the
 * JPQL + partial index combination behaves correctly — no mocks.
 */
class ChatMessageRepositoryTest extends IntegrationTestBase {

    @Autowired
    private ChatMessageRepository repo;

    private final UUID SESSION_ID = UUID.randomUUID();
    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID EVENT_ID = UUID.randomUUID();

    @BeforeEach
    void seed() {
        // Insert 20 messages with 100ms gaps
        Instant base = Instant.parse("2026-04-12T10:00:00Z");
        for (int i = 0; i < 20; i++) {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(SESSION_ID);
            msg.setTenantId(TENANT_ID);
            msg.setEventId(EVENT_ID);
            msg.setUserId(UUID.randomUUID());
            msg.setMessageType(MessageType.USER);
            msg.setText("Message " + i);
            msg.setCreatedAt(base.plusMillis(i * 100));
            msg.setDeleted(false);
            msg.setHidden(false);
            repo.save(msg);
        }
    }

    @Test
    @DisplayName("findLiveFeed returns newest messages first")
    void liveFeedReturnsNewestFirst() {
        List<ChatMessage> result = repo.findLiveFeed(SESSION_ID, PageRequest.of(0, 5));
        assertThat(result).hasSize(5);
        // Newest first
        assertThat(result.get(0).getText()).isEqualTo("Message 19");
        assertThat(result.get(4).getText()).isEqualTo("Message 15");
    }

    @Test
    @DisplayName("findLiveFeedBefore returns older messages via cursor")
    void cursorPaginationLoadsOlderMessages() {
        // Get initial page
        List<ChatMessage> page1 = repo.findLiveFeed(SESSION_ID, PageRequest.of(0, 5));
        ChatMessage lastOfPage1 = page1.get(page1.size() - 1);

        // Load next page using cursor
        List<ChatMessage> page2 = repo.findLiveFeedBefore(
                SESSION_ID,
                lastOfPage1.getCreatedAt(),
                lastOfPage1.getId(),
                PageRequest.of(0, 5));

        assertThat(page2).hasSize(5);
        // All messages in page2 should be older than the cursor
        for (ChatMessage msg : page2) {
            assertThat(msg.getCreatedAt()).isBeforeOrEqualTo(lastOfPage1.getCreatedAt());
        }
        // No overlap between pages
        assertThat(page2).noneMatch(m -> page1.contains(m));
    }

    @Test
    @DisplayName("findLiveFeedAfter returns newer messages via cursor")
    void cursorPaginationLoadsNewerMessages() {
        // Take an old message as cursor
        List<ChatMessage> all = repo.findLiveFeed(SESSION_ID, PageRequest.of(0, 20));
        ChatMessage oldMsg = all.get(all.size() - 1); // oldest

        // Load newer messages since that cursor
        List<ChatMessage> newer = repo.findLiveFeedAfter(
                SESSION_ID,
                oldMsg.getCreatedAt(),
                oldMsg.getId(),
                PageRequest.of(0, 100));

        // Should return everything except the oldest
        assertThat(newer).hasSize(19);
        // Oldest first (ascending for "after" queries)
        assertThat(newer.get(0).getCreatedAt()).isAfter(oldMsg.getCreatedAt());
    }

    @Test
    @DisplayName("Deleted messages are excluded from live feed")
    void deletedMessagesExcluded() {
        // Soft-delete a message
        List<ChatMessage> all = repo.findLiveFeed(SESSION_ID, PageRequest.of(0, 20));
        ChatMessage toDelete = all.get(0);
        toDelete.setDeleted(true);
        repo.save(toDelete);

        List<ChatMessage> afterDelete = repo.findLiveFeed(SESSION_ID, PageRequest.of(0, 20));
        assertThat(afterDelete).hasSize(19);
        assertThat(afterDelete).noneMatch(m -> m.getId().equals(toDelete.getId()));
    }
}
