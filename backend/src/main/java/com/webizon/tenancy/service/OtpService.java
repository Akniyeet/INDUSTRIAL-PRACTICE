package com.webizon.tenancy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webizon.tenancy.api.dto.TokenResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP service with token escrow.
 *
 * Flow:
 * 1. otp/request: validate creds → get tokens → store {code, tokens} in Redis → email code
 * 2. otp/verify: check code → return stored tokens → delete from Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String OTP_CODE_PREFIX = "otp:code:";
    private static final String OTP_TOKEN_PREFIX = "otp:token:";
    private static final String RATE_PREFIX = "otp:rate:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generate OTP, store code + tokens in Redis, send code via email.
     */
    public void generateAndSend(String email, TokenResponse tokens) {
        String key = email.toLowerCase();

        // Rate limit
        String rateKey = RATE_PREFIX + key;
        Long attempts = redis.opsForValue().increment(rateKey);
        if (attempts != null && attempts == 1) redis.expire(rateKey, RATE_WINDOW);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много попыток. Подождите 15 минут.");
        }

        // Generate 6-digit code
        String code = String.valueOf(random.nextInt(900_000) + 100_000);

        // Store code and tokens in Redis
        redis.opsForValue().set(OTP_CODE_PREFIX + key, code, TTL);
        try {
            String tokensJson = objectMapper.writeValueAsString(tokens);
            redis.opsForValue().set(OTP_TOKEN_PREFIX + key, tokensJson, TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tokens", e);
        }

        // Send email
        sendEmail(email, code);
        log.info("OTP sent to {}", email);
    }

    /**
     * Verify OTP code and return escrowed tokens.
     */
    public TokenResponse verifyAndGetTokens(String email, String code) {
        String key = email.toLowerCase();
        String codeKey = OTP_CODE_PREFIX + key;
        String tokenKey = OTP_TOKEN_PREFIX + key;

        String stored = redis.opsForValue().get(codeKey);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Код истёк. Запросите новый код.");
        }
        if (!stored.equals(code.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Неверный код. Проверьте почту.");
        }

        // Get stored tokens
        String tokensJson = redis.opsForValue().get(tokenKey);
        if (tokensJson == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Сессия истекла. Войдите заново.");
        }

        // Cleanup Redis (one-time use)
        redis.delete(codeKey);
        redis.delete(tokenKey);

        try {
            return objectMapper.readValue(tokensJson, TokenResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize tokens", e);
        }
    }

    private void sendEmail(String email, String code) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(email);
            helper.setFrom("webizon365@gmail.com", "Webizon");
            helper.setReplyTo("webizon365@gmail.com", "Webizon Support");
            helper.setSubject("Ваш код для входа в Webizon");
            // Anti-spam: set proper headers
            mimeMessage.setHeader("X-Mailer", "Webizon Platform");
            mimeMessage.setHeader("X-Priority", "1");
            mimeMessage.setHeader("Precedence", "bulk");
            mimeMessage.setHeader("List-Unsubscribe", "<mailto:webizon365@gmail.com?subject=unsubscribe>");
            // Plain text fallback (important for spam filters)
            String plainText = "Ваш код подтверждения: " + code + "\n\nКод действителен 5 минут.\nЕсли вы не запрашивали код, проигнорируйте это письмо.\n\n— Webizon";
            helper.setText(plainText, buildHtmlEmail(code));
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
        }
    }

    private String buildHtmlEmail(String code) {
        // Split code into individual digits for styled display
        StringBuilder digits = new StringBuilder();
        for (char c : code.toCharArray()) {
            digits.append(String.format(
                "<td style=\"width:48px;height:56px;background:#f0f4ff;border:2px solid #d4deff;" +
                "border-radius:12px;text-align:center;font-size:28px;font-weight:800;" +
                "color:#215CFF;font-family:'DM Sans',Arial,sans-serif;letter-spacing:0\">" +
                "%c</td><td style=\"width:8px\"></td>", c));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f8fafc;font-family:'DM Sans',-apple-system,Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;padding:40px 20px">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.06)">

                    <!-- Header with gradient -->
                    <tr>
                      <td style="background:linear-gradient(135deg,#0f172a 0%%,#1e293b 100%%);padding:32px 40px;text-align:center">
                        <img src="https://i.imgur.com/placeholder.png" width="0" height="0" style="display:none" />
                        <div style="font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.5px">Webizon</div>
                        <div style="margin-top:4px;font-size:13px;color:rgba(255,255,255,0.5)">Платформа для вебинаров</div>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:40px 40px 20px;text-align:center">
                        <!-- Lock icon -->
                        <div style="width:56px;height:56px;margin:0 auto;background:#f0f4ff;border-radius:16px;line-height:56px;font-size:24px">
                          &#128274;
                        </div>
                        <h1 style="margin:20px 0 8px;font-size:22px;font-weight:700;color:#0f172a">
                          Код подтверждения
                        </h1>
                        <p style="margin:0;font-size:14px;color:#64748b;line-height:1.5">
                          Введите этот код для входа в аккаунт
                        </p>
                      </td>
                    </tr>

                    <!-- OTP Code -->
                    <tr>
                      <td style="padding:16px 40px 32px;text-align:center">
                        <table cellpadding="0" cellspacing="0" style="margin:0 auto">
                          <tr>
                            """ + digits.toString() + """
                          </tr>
                        </table>
                      </td>
                    </tr>

                    <!-- Copy-friendly code text -->
                    <tr>
                      <td style="padding:0 40px 20px;text-align:center">
                        <div style="display:inline-block;background:#f0f4ff;border:2px solid #d4deff;border-radius:12px;padding:12px 32px">
                          <span style="font-size:32px;font-weight:900;color:#215CFF;letter-spacing:8px;font-family:'DM Sans',monospace;user-select:all">""" + code + """</span>
                        </div>
                        <div style="margin-top:10px;font-size:12px;color:#94a3b8">
                          &#128203; Нажмите на код, чтобы выделить и скопировать
                        </div>
                      </td>
                    </tr>

                    <!-- Timer badge -->
                    <tr>
                      <td style="padding:0 40px 32px;text-align:center">
                        <div style="display:inline-block;background:#fef3c7;color:#92400e;font-size:12px;font-weight:600;padding:6px 16px;border-radius:20px">
                          &#9200; Код действителен 5 минут
                        </div>
                      </td>
                    </tr>

                    <!-- Divider -->
                    <tr>
                      <td style="padding:0 40px">
                        <div style="height:1px;background:#e2e8f0"></div>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="padding:24px 40px 32px;text-align:center">
                        <p style="margin:0;font-size:12px;color:#94a3b8;line-height:1.5">
                          Если вы не запрашивали этот код, просто проигнорируйте это письмо.<br>
                          Никто не сможет войти в ваш аккаунт без этого кода.
                        </p>
                      </td>
                    </tr>

                    <!-- Brand footer -->
                    <tr>
                      <td style="background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #f1f5f9">
                        <p style="margin:0;font-size:11px;color:#94a3b8">
                          &copy; 2026 Webizon &middot; Казахстан
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """;
    }
}
