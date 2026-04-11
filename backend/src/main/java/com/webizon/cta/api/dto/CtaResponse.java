package com.webizon.cta.api.dto;

import com.webizon.cta.model.CtaPlacement;
import com.webizon.cta.model.CtaType;
import com.webizon.cta.model.EventCta;

import java.time.Instant;
import java.util.UUID;

public record CtaResponse(
        UUID id,
        UUID eventId,
        String title,
        String description,
        CtaType type,
        String buttonText,
        String actionUrl,
        String fileUrl,
        CtaPlacement placement,
        int priority,
        boolean allowStack,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static CtaResponse from(EventCta c) {
        return new CtaResponse(
                c.getId(),
                c.getEventId(),
                c.getTitle(),
                c.getDescription(),
                c.getType(),
                c.getButtonText(),
                c.getActionUrl(),
                c.getFileUrl(),
                c.getPlacement(),
                c.getPriority(),
                c.isAllowStack(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
