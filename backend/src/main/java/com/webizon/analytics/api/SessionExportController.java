package com.webizon.analytics.api;

import com.webizon.analytics.service.SessionExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * XLSX export endpoint for a session — Bizon365-style report with
 * summary, viewers, and chat transcript sheets.
 *
 * <p>Gated to owner / admin / analyst roles. The file streams directly
 * to the response without buffering the full workbook in memory
 * (thanks to {@link org.apache.poi.xssf.streaming.SXSSFWorkbook}).
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/export")
@RequiredArgsConstructor
public class SessionExportController {

    private final SessionExportService exportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST')")
    public void exportXlsx(@PathVariable UUID sessionId,
                           HttpServletResponse response) throws IOException {
        String filename = exportService.exportFilename(sessionId);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        exportService.export(sessionId, response.getOutputStream());
        response.flushBuffer();
    }
}
