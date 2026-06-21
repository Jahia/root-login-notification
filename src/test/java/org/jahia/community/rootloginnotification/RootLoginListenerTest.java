package org.jahia.community.rootloginnotification;

import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.services.mail.MailService;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        doThrow(new RuntimeException("SMTP down")).when(mailService)
                .sendMessage(any(), any(), any(), any(), any(), any(), any());
        HttpServletRequest req = request("10.0.0.1", null, "host", "site");

        // Must not propagate: a notification failure cannot break the login flow.
        listener.onEvent(rootEvent(req));

        verify(mailService).sendMessage(any(), any(), any(), any(), any(), any(), any());
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
        verify(mailService).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void skipsSendWhenNoRecipientAvailable() {
        when(mailService.defaultRecipient()).thenReturn(null);
        when(config.getRecipient()).thenReturn(null);
        HttpServletRequest req = request("10.0.0.1", null, "host", "site");

        listener.onEvent(rootEvent(req));

        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }
}
