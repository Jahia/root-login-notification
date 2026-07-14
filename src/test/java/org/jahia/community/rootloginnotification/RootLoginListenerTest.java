package org.jahia.community.rootloginnotification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.services.mail.MailService;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RootLoginListenerTest {

    private MailService mailService;
    private RootLoginNotificationConfig config;
    private RootLoginListener listener;

    @Before
    public void setUp() {
        mailService = mock(MailService.class);
        config = mock(RootLoginNotificationConfig.class);
        listener = new RootLoginListener();
        listener.setMailService(mailService);
        listener.setConfig(config);

        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.defaultSender()).thenReturn("noreply@example.com");
        when(mailService.defaultRecipient()).thenReturn("security@example.com");
        when(config.getSubject()).thenReturn("[{server} - {site}] root login");
        when(config.getBody()).thenReturn("IP: {ip} at {time}");
    }

    private BaseLoginEvent rootEvent(HttpServletRequest request) {
        BaseLoginEvent event = mock(BaseLoginEvent.class);
        JahiaUser user = mock(JahiaUser.class);
        when(user.isRoot()).thenReturn(true);
        AuthValveContext ctx = mock(AuthValveContext.class);
        when(event.getJahiaUser()).thenReturn(user);
        when(event.getAuthValveContext()).thenReturn(ctx);
        // getTimestamp() is final on Spring's ApplicationEvent — cannot be stubbed; mock returns 0L.
        when(ctx.getRequest()).thenReturn(request);
        return event;
    }

    private HttpServletRequest request(String xff, String remoteAddr, String server, String site) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-forwarded-for")).thenReturn(xff);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getServerName()).thenReturn(server);
        when(request.getParameter("site")).thenReturn(site);
        return request;
    }

    private String sentSubject() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(any(), any(), any(), any(), c.capture(), any(), any());
        return c.getValue();
    }

    private String sentBody() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(any(), any(), any(), any(), any(), any(), c.capture());
        return c.getValue();
    }

    // ── log capture (S6 / U4) ─────────────────────────────────────────────────────
    private ch.qos.logback.classic.Logger listenerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    private ListAppender<ILoggingEvent> attachLogAppender() {
        listenerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RootLoginListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        listenerLogger.addAppender(logAppender);
        return logAppender;
    }

    @After
    public void detachLogAppender() {
        if (listenerLogger != null && logAppender != null) {
            listenerLogger.detachAppender(logAppender);
        }
    }

    @Test
    public void sendsNotificationForRootLoginWithSanitizedValues() {
        HttpServletRequest req = request("203.0.113.7", null, "host.example.com", "mysite");
        listener.onEvent(rootEvent(req));

        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(sender.capture(), recipient.capture(), any(), any(),
                subject.capture(), any(), body.capture());

        assertThat(sender.getValue()).isEqualTo("noreply@example.com");
        assertThat(recipient.getValue()).isEqualTo("security@example.com");
        assertThat(subject.getValue()).isEqualTo("[host.example.com - mysite] root login");
        assertThat(body.getValue()).contains("203.0.113.7");
    }

    @Test
    public void doesNotSendForNonRootUser() {
        BaseLoginEvent event = mock(BaseLoginEvent.class);
        JahiaUser user = mock(JahiaUser.class);
        when(user.isRoot()).thenReturn(false);
        when(event.getJahiaUser()).thenReturn(user);

        listener.onEvent(event);

        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void doesNotSendWhenMailServiceDisabled() {
        when(mailService.isEnabled()).thenReturn(false);

        listener.onEvent(rootEvent(request(null, "10.0.0.1", "h", "s")));

        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void stripsCrlfHeaderInjectionFromServerName() {
        HttpServletRequest req = request("10.0.0.1", null,
                "host\r\nBcc: attacker@evil.com", "mysite");
        listener.onEvent(rootEvent(req));

        // The header-injection vector is the CR/LF that would start a new header line; it must be
        // stripped. Residual plain text on the same line is harmless (still inside the subject).
        String subject = sentSubject();
        assertThat(subject).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    public void stripsCrlfHeaderInjectionFromSiteParameter() {
        HttpServletRequest req = request("10.0.0.1", null, "host",
                "site\r\nCc: attacker@evil.com");
        listener.onEvent(rootEvent(req));

        String subject = sentSubject();
        assertThat(subject).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    public void stripsCrlfFromConfiguredRecipientAndSender() {
        when(config.getRecipient()).thenReturn("ok@example.com\r\nBcc: evil@evil.com");
        when(config.getSender()).thenReturn("from@example.com\nX-Injected: 1");
        HttpServletRequest req = request("10.0.0.1", null, "host", "site");
        listener.onEvent(rootEvent(req));

        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(sender.capture(), recipient.capture(), any(), any(),
                any(), any(), any());

        assertThat(recipient.getValue()).doesNotContain("\r").doesNotContain("\n");
        assertThat(sender.getValue()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    public void keepsFirstEntryOfXForwardedForChain() {
        HttpServletRequest req = request("198.51.100.9, 10.0.0.1, 10.0.0.2", null, "host", "site");
        listener.onEvent(rootEvent(req));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(any(), any(), any(), any(), any(), any(), body.capture());
        assertThat(body.getValue()).contains("198.51.100.9").doesNotContain("10.0.0.2");
    }

    @Test
    public void doesNotThrowWhenSendMessageFails() {
        // Capture the thread the send runs on, then throw to prove the failure is swallowed.
        final AtomicReference<Thread> sendThread = new AtomicReference<>();
        doAnswer(inv -> {
            sendThread.set(Thread.currentThread());
            throw new RuntimeException("SMTP down");
        }).when(mailService).sendMessage(any(), any(), any(), any(), any(), any(), any());
        HttpServletRequest req = request("10.0.0.1", null, "host", "site");

        // Must not propagate: a notification failure cannot break the login flow.
        listener.onEvent(rootEvent(req));

        verify(mailService).sendMessage(any(), any(), any(), any(), any(), any(), any());
        // CHARACTERIZATION (U3): the send is synchronous on the login-event thread — no async
        // dispatch, no timeout. A slow/failing SMTP therefore delays the login. Frames the Stage-7
        // async/timeout decision; flip if dispatch becomes asynchronous.
        assertThat(sendThread.get()).isSameAs(Thread.currentThread());
    }

    @Test
    public void doesNotThrowWhenJahiaUserIsNull() {
        BaseLoginEvent event = mock(BaseLoginEvent.class);
        when(event.getJahiaUser()).thenReturn(null);

        listener.onEvent(event);

        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void doesNotThrowWhenRequestIsNull() {
        BaseLoginEvent event = rootEvent(null);

        listener.onEvent(event);

        // No request -> empty host/ip, but recipient default present, so mail still sent without throwing.
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(any(), any(), any(), any(), subject.capture(), any(), body.capture());

        // NEW (S5 degradation): document the null-request degradation — {server} resolves to empty
        // string and {site} to the "systemsite" default; {ip} resolves to empty.
        assertThat(subject.getValue()).isEqualTo("[ - systemsite] root login");
        assertThat(body.getValue()).startsWith("IP:  at "); // empty {ip} -> two spaces before "at"
    }

    @Test
    public void skipsSendWhenNoRecipientAvailable() {
        when(mailService.defaultRecipient()).thenReturn(null);
        when(config.getRecipient()).thenReturn(null);
        HttpServletRequest req = request("10.0.0.1", null, "host", "site");
        ListAppender<ILoggingEvent> logs = attachLogAppender();

        listener.onEvent(rootEvent(req));

        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
        // NEW (S6 / U4 / D6): the no-recipient path is NOT silent — it emits a WARN. This
        // distinguishes it from the truly-silent skips (non-root, mail disabled).
        assertThat(logs.list)
                .anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.WARN);
                    assertThat(e.getFormattedMessage()).contains("no recipient configured");
                });
    }

    // ── S14 — U1 REMEDIATED: XFF is advisory, real socket peer surfaced alongside it ─

    @Test
    public void forgedXForwardedForIsShownAlongsideRealSocketPeer() {
        // REMEDIATION (U1): x-forwarded-for is client-controllable and therefore advisory. The alert
        // now surfaces the real TCP socket peer (getRemoteAddr) alongside the forwarded value, so a
        // spoofed XFF can no longer HIDE the actual peer. Both values must appear in the body.
        HttpServletRequest req = request("1.2.3.4", "10.0.0.9", "host", "site");
        listener.onEvent(rootEvent(req));

        String body = sentBody();
        assertThat(body).contains("1.2.3.4");   // forwarded (advisory) value still reported
        assertThat(body).contains("10.0.0.9");  // real socket peer now also reported — cannot be hidden
    }

    // ── S15 — U2 DOCUMENTED DECISION: Host header remains advisory (accepted limitation) ─

    @Test
    public void forgedHostHeaderIsUsedVerbatimInSubject() {
        // DOCUMENTED DECISION (U2): the Host header is client-controllable, but unlike the IP there is
        // no server-authoritative alternative to surface without a configured expected-host allowlist.
        // Per the Stage-7 minimal-hardening decision this is accepted and documented in the trust model
        // (README/AGENTS "Security & trust model"): {server} is advisory. sanitizeHost still strips any
        // CR/LF header-injection vector; only forgery (not injection) is out of scope here.
        HttpServletRequest req = request("10.0.0.1", null, "evil-lookalike.example.com", "site");
        listener.onEvent(rootEvent(req));

        assertThat(sentSubject()).contains("evil-lookalike.example.com");
    }

    // ── S11 — HTML-body XSS defense for {ip} (char-strip + escapeHtml) ───────────────

    @Test
    public void stripsAndEscapesXssPayloadInBodyIp() {
        when(config.getBody()).thenReturn("Connection IP: {ip} at {time}");
        HttpServletRequest req = request("1.2.3.4<script>alert(1)</script>&\"", null, "host", "site");
        listener.onEvent(rootEvent(req));

        // sanitizeHost strips everything but [A-Za-z0-9._:\-] (leaving 1.2.3.4scriptalert1script),
        // and any residual metacharacter would be HTML-escaped. No raw markup reaches the body.
        String body = sentBody();
        assertThat(body).doesNotContain("<script>").doesNotContain("</script>");
        assertThat(body).doesNotContain("<").doesNotContain(">").doesNotContain("\"");
        assertThat(body).contains("1.2.3.4scriptalert1script");
    }

    // ── S12 — 255-char truncation on host/header/address sanitizers ──────────────────

    @Test
    public void truncatesLongValuesToMaxHeaderLength() {
        // Subject is JUST {server}: proves both sanitizeHost (300 -> 255) and the final subject-level
        // sanitizeHeader cap keep the value at MAX_HEADER_VALUE_LENGTH (255).
        String longHostChars = repeat("a", 300);          // valid host chars
        String longAddress = repeat("c", 300) + "@e.co";  // valid address chars (sanitizeAddress)
        when(config.getSubject()).thenReturn("{server}");
        when(config.getRecipient()).thenReturn(longAddress);
        HttpServletRequest req = request(null, "10.0.0.1", longHostChars, "site");
        listener.onEvent(rootEvent(req));

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(any(), recipient.capture(), any(), any(),
                subject.capture(), any(), any());

        assertThat(subject.getValue().length()).isEqualTo(255);            // {server} via sanitizeHost + header cap
        assertThat(recipient.getValue().length()).isLessThanOrEqualTo(255); // via sanitizeAddress
    }

    @Test
    public void truncatesLongSiteViaHeaderSanitizer() {
        // Site flows through resolveSite -> sanitizeHeader; a 300-char site must be capped to 255.
        String longSite = repeat("b", 300);
        when(config.getSubject()).thenReturn("{site}");
        HttpServletRequest req = request(null, "10.0.0.1", "host", longSite);
        listener.onEvent(rootEvent(req));

        assertThat(sentSubject().length()).isEqualTo(255);
    }

    // ── S16 — x-forwarded-for absent -> getRemoteAddr() fallback ─────────────────────

    @Test
    public void fallsBackToRemoteAddrWhenXForwardedForAbsent() {
        HttpServletRequest req = request(null, "192.0.2.55", "host", "site");
        listener.onEvent(rootEvent(req));

        assertThat(sentBody()).contains("192.0.2.55");
    }

    // ── S17 — {time} formatting seam (server-default timezone) ───────────────────────

    @Test
    public void formatLoginTimeMatchesServerZonePattern() {
        long epoch = 0L;
        String formatted = RootLoginListener.formatLoginTime(epoch);

        assertThat(formatted).matches("^\\d{4}/\\d{2}/\\d{2} at \\d{2}:\\d{2}:\\d{2} .+$");

        // Equals the documented formatter applied on the SAME instant in the server default zone
        // (not UTC) — so ops know the displayed time depends on the JVM timezone.
        DateTimeFormatter expected = DateTimeFormatter
                .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
                .withZone(ZoneId.systemDefault());
        assertThat(formatted).isEqualTo(expected.format(Instant.ofEpochMilli(epoch)));
        assertThat(RootLoginListener.formatLoginTime(1_700_000_000_000L))
                .isEqualTo(expected.format(Instant.ofEpochMilli(1_700_000_000_000L)));
    }

    // ── S18 — tokens do NOT work cross-field ─────────────────────────────────────────

    @Test
    public void tokensAreNotSubstitutedCrossField() {
        when(config.getSubject()).thenReturn("srv={server} ip={ip}");
        when(config.getBody()).thenReturn("time={time} site={site}");
        HttpServletRequest req = request("203.0.113.7", null, "host.example.com", "mysite");
        listener.onEvent(rootEvent(req));

        // subject only replaces {server}/{site}; {ip} stays literal.
        assertThat(sentSubject()).contains("srv=host.example.com").contains("ip={ip}");
        // body only replaces {ip}/{time}; {site} stays literal, {time} is replaced (not literal).
        String body = sentBody();
        assertThat(body).contains("site={site}").doesNotContain("time={time}");
    }

    // ── S40 — non-BaseLoginEvent ignored; event-type contract ────────────────────────

    @Test
    public void ignoresNonBaseLoginEventAndExposesEventTypeContract() {
        // A plain EventObject is not a BaseLoginEvent -> guard returns early, nothing sent.
        // NOTE (U5): root access via impersonation / API token / SSO / JWT flows that never emit
        // a BaseLoginEvent produces NO notification by design; unobservable at unit level (no event).
        listener.onEvent(new EventObject("src"));
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());

        assertThat(new RootLoginListener().getEventTypes())
                .containsExactly(BaseLoginEvent.class);
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
