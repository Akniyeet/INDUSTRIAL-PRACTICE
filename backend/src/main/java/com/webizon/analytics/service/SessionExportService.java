package com.webizon.analytics.service;

import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.model.SessionAttendance;
import com.webizon.analytics.repo.AnalyticsEventRepository;
import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.events.model.Event;
import com.webizon.events.model.Session;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Builds an XLSX export for a finished (or live) session, mimicking the
 * Bizon365 post-broadcast report format that CRM teams are used to.
 *
 * <h2>Sheet structure</h2>
 * <ol>
 *   <li><b>Сведения</b> — session summary: room, title, start, duration,
 *       peak/total viewers, conversion counters</li>
 *   <li><b>Зрители</b> — one row per attendee: name placeholder (profileId),
 *       joined at, left at, total watch, device, CTA clicks, city via metadata</li>
 *   <li><b>Чат</b> — full non-deleted chat transcript with offset timestamps</li>
 * </ol>
 *
 * Uses {@link SXSSFWorkbook} (streaming) to keep heap bounded even for
 * sessions with 60K viewers.
 */
@Service
@RequiredArgsConstructor
public class SessionExportService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AnalyticsReportService analyticsReportService;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Almaty"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Almaty"));

    /**
     * Writes the XLSX workbook for the given session directly to the
     * output stream.  Caller is responsible for setting Content-Type
     * and Content-Disposition headers.
     */
    @Transactional(readOnly = true)
    public void export(UUID sessionId, OutputStream out) throws IOException {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
        Event event = eventRepository.findById(session.getEventId())
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + session.getEventId()));

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle boldStyle = createBoldStyle(wb);

            writeSummarySheet(wb, session, event, headerStyle, boldStyle);
            writeViewersSheet(wb, session, event, headerStyle);
            writeChatSheet(wb, session, headerStyle);

            wb.write(out);
        }
    }

    /**
     * Generates a filename like:
     * {@code session_574855ff_2026-04-06_18-00.xlsx}
     */
    public String exportFilename(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(s -> {
                    String id = sessionId.toString().substring(0, 8);
                    String ts = s.getStartTime() != null
                            ? DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                                .withZone(ZoneId.of("Asia/Almaty"))
                                .format(s.getStartTime())
                            : "unknown";
                    return "session_" + id + "_" + ts + ".xlsx";
                })
                .orElse("session_export.xlsx");
    }

    // ── Sheet 1: Сведения ──────────────────────────────────────────────

    private void writeSummarySheet(SXSSFWorkbook wb, Session session, Event event,
                                   CellStyle headerStyle, CellStyle boldStyle) {
        Sheet sheet = wb.createSheet("Сведения");
        sheet.setColumnWidth(0, 38 * 256);
        sheet.setColumnWidth(1, 28 * 256);

        int r = 0;
        r = addSummaryRow(sheet, r, "Событие", event.getTitle(), boldStyle);
        r = addSummaryRow(sheet, r, "Slug", event.getSlug(), null);
        r = addSummaryRow(sheet, r, "Спикер", event.getSpeakerName(), null);
        r++;

        String startStr = session.getActualStartedAt() != null
                ? DT_FMT.format(session.getActualStartedAt())
                : (session.getStartTime() != null ? DT_FMT.format(session.getStartTime()) : "—");
        r = addSummaryRow(sheet, r, "Начало:", startStr, boldStyle);

        int durationMin = session.getPlannedDurationSeconds() / 60;
        if (session.getActualStartedAt() != null && session.getActualEndedAt() != null) {
            durationMin = (int) Duration.between(session.getActualStartedAt(), session.getActualEndedAt()).toMinutes();
        }
        r = addSummaryRow(sheet, r, "Длительность, минут:", String.valueOf(durationMin), null);
        r = addSummaryRow(sheet, r, "Тип сессии:", session.getType().name(), null);
        r = addSummaryRow(sheet, r, "Статус:", session.getStatus().name(), null);
        r++;

        AnalyticsReportService.SessionSummary summary = analyticsReportService.summarise(session.getId());

        r = addSummaryRow(sheet, r, "Всего зрителей:", String.valueOf(summary.totalAttendees()), boldStyle);
        r = addSummaryRow(sheet, r, "Смотрели > 30 мин:", String.valueOf(summary.watchedLongCount()), null);
        r = addSummaryRow(sheet, r, "Сообщений в чате:", String.valueOf(summary.chatMessages()), null);
        r = addSummaryRow(sheet, r, "Кликов по CTA:", String.valueOf(summary.ctaClicks()), null);
        r = addSummaryRow(sheet, r, "Модерация событий:", String.valueOf(summary.moderationEventCount()), null);
        r++;

        // Retention curve
        List<AnalyticsReportService.RetentionPoint> curve = analyticsReportService.retentionCurve(session.getId());
        r = addSummaryRow(sheet, r, "Retention", "", boldStyle);
        for (AnalyticsReportService.RetentionPoint pt : curve) {
            String label = pt.offsetSeconds() == 0 ? "Старт" : (pt.offsetSeconds() / 60) + " мин";
            r = addSummaryRow(sheet, r, "  " + label, String.valueOf(pt.viewers()), null);
        }
        r++;

        // CTA CTR
        List<AnalyticsReportService.CtaCtrRow> ctaRows = analyticsReportService.ctaCtr(event.getId(), session.getId());
        if (!ctaRows.isEmpty()) {
            r = addSummaryRow(sheet, r, "CTA", "", boldStyle);
            for (AnalyticsReportService.CtaCtrRow cta : ctaRows) {
                r = addSummaryRow(sheet, r, "  " + cta.title() + " (" + cta.type() + ")",
                        cta.clicks() + " кликов / " + cta.impressions() + " показов = " +
                                String.format("%.1f%%", cta.ctr() * 100), null);
            }
        }
    }

    private int addSummaryRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell c0 = row.createCell(0);
        c0.setCellValue(label);
        if (style != null) c0.setCellStyle(style);
        row.createCell(1).setCellValue(value != null ? value : "");
        return rowNum + 1;
    }

    // ── Sheet 2: Зрители ───────────────────────────────────────────────

    private void writeViewersSheet(SXSSFWorkbook wb, Session session, Event event,
                                   CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Зрители");

        String[] headers = {
                "Дата", "ID пользователя", "На сессии с", "Досмотрел до",
                "Всего секунд", "Заходов", "Макс. offset (сек)",
                "Клик по CTA", "Клик по баннеру"
        };

        Row hRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Attendees
        List<SessionAttendance> attendees = attendanceRepository
                .findAllBySessionIdOrderByFirstJoinedAtAsc(session.getId());

        // Profiles who clicked CTA
        Set<UUID> ctaClickers = new HashSet<>(analyticsEventRepository
                .findDistinctProfileIdsBySessionIdAndEventType(
                        session.getId(), AnalyticsEventType.CTA_CLICK));

        String dateStr = session.getStartTime() != null ? DT_FMT.format(session.getStartTime()) : "";

        int r = 1;
        for (SessionAttendance a : attendees) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(dateStr);
            row.createCell(1).setCellValue(a.getProfileId().toString());
            row.createCell(2).setCellValue(TIME_FMT.format(a.getFirstJoinedAt()));
            row.createCell(3).setCellValue(a.getLastSeenAt() != null ? TIME_FMT.format(a.getLastSeenAt()) : "—");
            row.createCell(4).setCellValue(a.getTotalConnectedSeconds());
            row.createCell(5).setCellValue(a.getEntryCount());
            row.createCell(6).setCellValue(a.getMaxOffsetSeconds() != null ? a.getMaxOffsetSeconds() : 0);
            row.createCell(7).setCellValue(ctaClickers.contains(a.getProfileId()) ? "Да" : "—");
            row.createCell(8).setCellValue("—");
        }

        // Auto-size is not available in SXSSF, set reasonable widths
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 38 * 256);
        for (int i = 2; i < headers.length; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    // ── Sheet 3: Чат ───────────────────────────────────────────────────

    private void writeChatSheet(SXSSFWorkbook wb, Session session, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Чат");

        String[] headers = {"Время", "ID отправителя", "Тип", "Сообщение"};
        Row hRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        List<ChatMessage> messages = chatMessageRepository.findExportTranscript(session.getId());

        int r = 1;
        for (ChatMessage m : messages) {
            Row row = sheet.createRow(r++);
            // Offset-based time like "00:05:23"
            String time;
            if (m.getOffsetSeconds() != null) {
                int secs = m.getOffsetSeconds();
                time = String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            } else if (m.getCreatedAt() != null) {
                time = TIME_FMT.format(m.getCreatedAt());
            } else {
                time = "—";
            }
            row.createCell(0).setCellValue(time);
            row.createCell(1).setCellValue(m.getUserId() != null ? m.getUserId().toString() : "SYSTEM");
            row.createCell(2).setCellValue(m.getMessageType().name());
            row.createCell(3).setCellValue(m.getText());
        }

        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 38 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 60 * 256);
    }

    // ── Styles ──────────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }
}
