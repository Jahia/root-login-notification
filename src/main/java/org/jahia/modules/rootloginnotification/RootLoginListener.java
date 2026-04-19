package org.jahia.modules.rootloginnotification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.jahia.params.valves.LoginEngineAuthValveImpl.LoginEvent;
import org.jahia.services.mail.MailService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.springframework.context.ApplicationListener;

@Component(immediate = true, service = ApplicationListener.class)
public final class RootLoginListener implements ApplicationListener<LoginEvent> {

    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private MailService mailService;
    private RootLoginNotificationConfig config;

    @Override
    public void onApplicationEvent(LoginEvent loginEvent) {
        if (mailService.isEnabled() && loginEvent.getJahiaUser().isRoot()) {
            final HttpServletRequest request = loginEvent.getAuthValveContext().getRequest();
            String remoteAddress = request.getHeader(REMOTE_ADDRESS_HEADER);
            if (remoteAddress == null) {
                remoteAddress = request.getRemoteAddr();
            }

            String site = request.getParameter("site");
            if (StringUtils.isEmpty(site)) {
                site = "systemsite";
            }
            final String serverName = request.getServerName();
            final String loginTime = DATE_FORMATTER.format(Instant.ofEpochMilli(loginEvent.getTimestamp()));

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

    @Reference
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Reference
    public void setConfig(RootLoginNotificationConfig config) {
        this.config = config;
    }
}
