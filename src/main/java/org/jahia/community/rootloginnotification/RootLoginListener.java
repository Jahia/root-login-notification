package org.jahia.community.rootloginnotification;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.services.mail.MailService;
import org.jahia.services.observation.JahiaEventListener;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.regex.Pattern;

@Component(immediate = true, service = JahiaEventListener.class)
public final class RootLoginListener implements JahiaEventListener<BaseLoginEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootLoginListener.class);

    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";
    private static final String DEFAULT_SITE = "systemsite";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    private static final Class<BaseLoginEvent>[] ALLOWED_EVENT_TYPES = new Class[]{BaseLoginEvent.class};
    // Conservative pattern: IPv4, IPv6 hex+colons, hostnames. Strips anything else.
    private static final Pattern SAFE_HOST_CHARS = Pattern.compile("[^A-Za-z0-9._:\\-]");
    // Characters legitimately found in an email address; everything else (incl. CR/LF) is stripped.
    private static final Pattern SAFE_ADDRESS_CHARS = Pattern.compile("[^A-Za-z0-9._%+\\-@]");
    private static final int MAX_HEADER_VALUE_LENGTH = 255;
    private static final char DEL_CHAR = 0x7F;
    private static final char FIRST_PRINTABLE_CHAR = 0x20;

    private MailService mailService;
    private RootLoginNotificationConfig config;

    @Override
    public void onEvent(EventObject event) {
        // A notification failure must never break the login flow: catch everything, log it, move on.
        try {
            handleLoginEvent(event);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to send root login notification email", e);
        }
    }

    private void handleLoginEvent(EventObject event) {
        if (!(event instanceof BaseLoginEvent)) {
            return;
        }
        final BaseLoginEvent baseLoginEvent = (BaseLoginEvent) event;

        if (mailService == null || config == null || !mailService.isEnabled()) {
            return;
        }

        final JahiaUser user = baseLoginEvent.getJahiaUser();
        if (user == null || !user.isRoot()) {
            return;
        }

        final AuthValveContext valveContext = baseLoginEvent.getAuthValveContext();
        final HttpServletRequest request = (valveContext != null) ? valveContext.getRequest() : null;

        final String remoteAddress = resolveRemoteAddress(request);
        final String site = sanitizeHeader(resolveSite(request));
        final String serverName = sanitizeHost(request != null ? request.getServerName() : null);
        final String loginTime = formatLoginTime(baseLoginEvent.getTimestamp());

        // recipient/sender may come from the OSGi .cfg file directly (bypassing the GraphQL
        // email-format validation), so sanitize CR/LF here as a header-injection defense.
        final String sender = sanitizeAddress(StringUtils.defaultIfEmpty(config.getSender(), mailService.defaultSender()));
        final String recipient = sanitizeAddress(StringUtils.defaultIfEmpty(config.getRecipient(), mailService.defaultRecipient()));

        // The subject is an email header; sanitize the fully-assembled value for CR/LF, since the
        // subject template itself (from config) is also user-controlled.
        final String subject = sanitizeHeader(StringUtils.defaultString(config.getSubject())
                .replace("{server}", serverName)
                .replace("{site}", site));
        final String body = StringUtils.defaultString(config.getBody())
                .replace("{ip}", StringEscapeUtils.escapeHtml(remoteAddress))
                .replace("{time}", StringEscapeUtils.escapeHtml(loginTime));

        if (StringUtils.isEmpty(recipient)) {
            LOGGER.warn("Root login notification skipped: no recipient configured and no MailService default available");
            return;
        }

        mailService.sendMessage(sender, recipient, null, null, subject, null, body);
    }

    /**
     * Formats a login timestamp (epoch millis) with {@code yyyy/MM/dd 'at' HH:mm:ss z} in the
     * server-default timezone. Extracted (package-private) so the {@code {time}} formatting can be
     * unit-tested directly: {@code BaseLoginEvent.getTimestamp()} is final and returns 0L under
     * Mockito, so it cannot be driven end-to-end. Behaviour is identical to the previous inline call.
     */
    static String formatLoginTime(long epochMillis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Builds the IP shown in the alert.
     *
     * <p>Trust model: {@code X-Forwarded-For} is client-controllable and therefore <em>advisory</em> —
     * any client can forge it, and this module does not enforce a trusted-proxy chain. So the real TCP
     * socket peer ({@code request.getRemoteAddr()}) is always surfaced alongside the forwarded value; a
     * spoofed XFF can no longer <em>hide</em> the actual peer that connected. When no XFF header is
     * present, or it equals the socket peer, only the socket peer is shown.
     *
     * <p>Each component is sanitized individually via {@link #sanitizeHost(String)} before the composite
     * is assembled (the composite itself is HTML-escaped by the caller before entering the body).
     */
    private static String resolveRemoteAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        final String socketAddress = sanitizeHost(request.getRemoteAddr());
        final String forwardedFor = sanitizeHost(firstForwardedForHop(request.getHeader(REMOTE_ADDRESS_HEADER)));

        if (forwardedFor.isEmpty()) {
            return socketAddress;
        }
        if (socketAddress.isEmpty() || forwardedFor.equals(socketAddress)) {
            return forwardedFor;
        }
        return forwardedFor + " (socket: " + socketAddress + ")";
    }

    /** x-forwarded-for may be a comma-separated chain; keep only the first entry (the purported client). */
    private static String firstForwardedForHop(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        final int comma = headerValue.indexOf(',');
        return (comma >= 0) ? headerValue.substring(0, comma) : headerValue;
    }

    private static String resolveSite(HttpServletRequest request) {
        final String site = (request != null) ? request.getParameter("site") : null;
        return StringUtils.isEmpty(site) ? DEFAULT_SITE : site;
    }

    /** Strips anything that is not a valid host/IP character to prevent header injection and XSS. */
    private static String sanitizeHost(String value) {
        if (value == null) {
            return "";
        }
        final String trimmed = StringUtils.left(value, MAX_HEADER_VALUE_LENGTH);
        return SAFE_HOST_CHARS.matcher(trimmed).replaceAll("");
    }

    /** Strips anything that is not a valid email-address character, removing CR/LF header-injection vectors. */
    private static String sanitizeAddress(String value) {
        if (value == null) {
            return "";
        }
        final String trimmed = StringUtils.left(value, MAX_HEADER_VALUE_LENGTH);
        return SAFE_ADDRESS_CHARS.matcher(trimmed).replaceAll("");
    }

    /** Removes CR/LF and other control characters that could be used for header injection. */
    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        final String trimmed = StringUtils.left(value, MAX_HEADER_VALUE_LENGTH);
        final StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (c >= FIRST_PRINTABLE_CHAR && c != DEL_CHAR) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public Class<BaseLoginEvent>[] getEventTypes() {
        return ALLOWED_EVENT_TYPES;
    }

    @Reference
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Reference
    public void setConfig(RootLoginNotificationConfig config) {
        this.config = config;
    }
}
