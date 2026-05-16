package org.jahia.modules.rootloginnotification;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.services.mail.MailService;
import org.jahia.services.observation.JahiaEventListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.regex.Pattern;

@Component(immediate = true, service = JahiaEventListener.class)
public final class RootLoginListener implements JahiaEventListener<BaseLoginEvent> {

    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    private static final Class<BaseLoginEvent>[] ALLOWED_EVENT_TYPES = new Class[]{BaseLoginEvent.class};
    // Conservative pattern: IPv4, IPv6 hex+colons, hostnames. Strips anything else.
    private static final Pattern SAFE_HOST_CHARS = Pattern.compile("[^A-Za-z0-9._:\\-]");
    private static final int MAX_HEADER_VALUE_LENGTH = 255;

    private MailService mailService;
    private RootLoginNotificationConfig config;

    @Override
    public void onEvent(EventObject event) {
        final BaseLoginEvent baseLoginEvent = (BaseLoginEvent) event;
        if (mailService.isEnabled() && baseLoginEvent.getJahiaUser().isRoot()) {
            final HttpServletRequest request = baseLoginEvent.getAuthValveContext().getRequest();
            String remoteAddress = request.getHeader(REMOTE_ADDRESS_HEADER);
            if (remoteAddress == null) {
                remoteAddress = request.getRemoteAddr();
            }
            // x-forwarded-for may contain a comma-separated chain; keep only the first entry (the client).
            if (remoteAddress != null) {
                final int comma = remoteAddress.indexOf(',');
                if (comma >= 0) {
                    remoteAddress = remoteAddress.substring(0, comma);
                }
            }
            // Sanitize user-controlled values inserted into mail subject/body to prevent header
            // injection (CRLF) and XSS in the HTML body.
            remoteAddress = sanitizeHost(remoteAddress);

            String site = request.getParameter("site");
            if (StringUtils.isEmpty(site)) {
                site = "systemsite";
            }
            site = sanitizeHeader(site);
            final String serverName = sanitizeHost(request.getServerName());
            final String loginTime = DATE_FORMATTER.format(Instant.ofEpochMilli(baseLoginEvent.getTimestamp()));

            final String sender = StringUtils.defaultIfEmpty(config.getSender(), mailService.defaultSender());
            final String recipient = StringUtils.defaultIfEmpty(config.getRecipient(), mailService.defaultRecipient());
            final String subject = config.getSubject()
                    .replace("{server}", serverName)
                    .replace("{site}", site);
            final String body = config.getBody()
                    .replace("{ip}", StringEscapeUtils.escapeHtml(remoteAddress))
                    .replace("{time}", StringEscapeUtils.escapeHtml(loginTime));

            mailService.sendMessage(sender, recipient, null, null, subject, null, body);
        }
    }

    /** Strips anything that is not a valid host/IP character to prevent header injection and XSS. */
    private static String sanitizeHost(String value) {
        if (value == null) {
            return "";
        }
        final String trimmed = StringUtils.left(value, MAX_HEADER_VALUE_LENGTH);
        return SAFE_HOST_CHARS.matcher(trimmed).replaceAll("");
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
            if (c >= 0x20 && c != 0x7F) {
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
