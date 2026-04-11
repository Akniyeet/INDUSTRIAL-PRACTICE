package com.webizon.realtime.api;

import com.webizon.auth.CurrentUser;
import com.webizon.realtime.ChannelAccessService;
import com.webizon.realtime.CentrifugoTokenSigner;
import com.webizon.realtime.CentrifugoTokenSigner.SignedToken;
import com.webizon.realtime.api.dto.ConnectTokenResponse;
import com.webizon.realtime.api.dto.SubscribeTokenRequest;
import com.webizon.realtime.api.dto.SubscribeTokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Token-minting endpoints for the Centrifugo real-time transport.
 *
 * <p>Centrifugo itself is the only party that terminates websockets from
 * browsers; this controller never touches a socket. Its job is to hand
 * the browser short-lived signed JWTs that Centrifugo then verifies on
 * {@code CONNECT} and {@code SUBSCRIBE} frames.
 *
 * <h2>Two-token model</h2>
 * <ol>
 *   <li><b>Connect token</b> — one per websocket session, identifies the
 *       user. The front-end asks for it immediately after login, opens
 *       the socket to Centrifugo, and refreshes on reconnect.</li>
 *   <li><b>Subscribe token</b> — one per (user, channel) pair, authorises
 *       the channel. The front-end requests it right before calling
 *       {@code centrifuge.subscribe(channel)}. This is where
 *       {@link ChannelAccessService} runs the permission rules.</li>
 * </ol>
 *
 * <p>Both endpoints require a valid Webizon JWT. Public/unauthenticated
 * landing page traffic does not interact with real-time at all — the
 * landing resolver is a plain REST call ({@code /api/v1/public/...}).
 *
 * <h2>Why POST, not GET</h2>
 * <p>Subscribe-token requests carry a channel name in the body. We do not
 * put it in the URL because it would land in access logs and browser
 * history — not sensitive per se, but the channel name embeds the
 * {@code tenantId} and {@code sessionId}, and keeping it out of URL
 * surfaces is good defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
@Slf4j
public class RealtimeController {

    private final CentrifugoTokenSigner tokenSigner;
    private final ChannelAccessService channelAccessService;

    /**
     * Mint a fresh connect token for the currently authenticated user.
     *
     * <p>Cheap and idempotent — the front-end calls this on every page
     * load and on websocket reconnection.
     */
    @PostMapping("/connect-token")
    public ConnectTokenResponse connectToken() {
        UUID profileId = CurrentUser.profileId();
        UUID tenantId = CurrentUser.tenantId().orElseThrow(
                () -> new AccessDeniedException("Authenticated user has no tenant binding"));
        String role = CurrentUser.role();

        SignedToken signed = tokenSigner.connectToken(profileId, tenantId, role);
        return new ConnectTokenResponse(signed.token(), signed.expiresAt());
    }

    /**
     * Mint a subscribe token for the requested channel, after checking
     * that the caller is allowed on it.
     *
     * <p>Returns {@code 403} via {@link AccessDeniedException} if the
     * channel is malformed, cross-tenant, missing, or privileged. We
     * deliberately do not distinguish these cases in the response — the
     * client only needs to know "you can't". Details go to the logs for
     * operator debugging.
     */
    @PostMapping("/subscribe-token")
    @ResponseStatus(HttpStatus.OK)
    public SubscribeTokenResponse subscribeToken(@Valid @RequestBody SubscribeTokenRequest request) {
        UUID profileId = CurrentUser.profileId();
        UUID tenantId = CurrentUser.tenantId().orElseThrow(
                () -> new AccessDeniedException("Authenticated user has no tenant binding"));
        String role = CurrentUser.role();

        ChannelAccessService.Decision decision = channelAccessService.authorize(
                profileId, tenantId, role, request.channel());

        if (!decision.allowed()) {
            log.info("Subscribe denied: user={} channel={} reason={}",
                    profileId, request.channel(), decision.denyReason());
            throw new AccessDeniedException("Subscribe denied");
        }

        SignedToken signed = tokenSigner.subscribeToken(profileId, request.channel());
        return new SubscribeTokenResponse(signed.token(), signed.expiresAt());
    }
}
