package org.jahia.modules.rootloginnotification;

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

@Component(immediate = true, service = JahiaEventListener.class)
public final class RootLoginListener implements JahiaEventListener<BaseLoginEvent> {

    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    private static final Class<BaseLoginEvent>[] ALLOWED_EVENT_TYPES = new Class[]{BaseLoginEvent.class};

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

            String site = request.getParameter("site");
            if (StringUtils.isEmpty(site)) {
                site = "systemsite";
            }
            final String serverName = request.getServerName();
            final String loginTime = DATE_FORMATTER.format(Instant.ofEpochMilli(baseLoginEvent.getTimestamp()));

            final String sender = StringUtils.defaultIfEmpty(config.getSender(), mailService.defaultSender());
            final String recipient = StringUtils.defaultIfEmpty(config.getRecipient(), mailService.defaultRecipient());
            final String subject = config.getSubject()
                    .replace("{server}", serverName)
                    .replace("{site}", site);
            final String body = config.getBody()
                    .replace("{ip}", remoteAddress)
                    .replace("{time}", loginTime);

            mailService.sendMessage(sender, recipient, null, null, subject, null, body);
        }
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
