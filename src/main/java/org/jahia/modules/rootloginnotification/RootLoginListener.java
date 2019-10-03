package org.jahia.modules.rootloginnotification;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.jahia.params.valves.LoginEngineAuthValveImpl.LoginEvent;
import org.jahia.services.mail.MailService;
import org.springframework.context.ApplicationListener;

/**
 *
 * @author fbourasse
 */
public final class RootLoginListener implements ApplicationListener<LoginEvent> {

    private static final String REMOTE_ADDRESS_HEADER = "x-forwarded-for";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss z");
    public MailService mailService;

    @Override
    public void onApplicationEvent(LoginEvent loginEvent) {
        System.out.println(loginEvent.getSource());
        System.out.println(loginEvent.getJahiaUser());

        if (mailService.isEnabled() && loginEvent.getJahiaUser().isRoot()) {
            final HttpServletRequest request = loginEvent.getAuthValveContext().getRequest();
            String remoteAddress = request.getHeader(REMOTE_ADDRESS_HEADER);
            if (remoteAddress == null) {
                remoteAddress = request.getRemoteAddr();
            }

            final Date loginDate = new Date(loginEvent.getTimestamp());
            String site = request.getParameter("site");
            if (StringUtils.isEmpty(site)) {
                site = "systemsite";
            }
            final String serverName = request.getServerName();
            final String sender = mailService.defaultSender();
            final String recipient = mailService.defaultRecipient();
            final String subject = "[%s - %s] Connection notification to the root account";
            final String body = "Hi,\n"
                    + "\n"
                    + "We're sending this email following a successful connection to the root user.\n"
                    + "\n"
                    + "    Connection IP     : %s\n"
                    + "    Connection time   : %s\n"
                    + "\n"
                    + "\n"
                    + "This email is meant to raise awareness about the secuirty of your services \n"
                    + "and to help you to protect them.\n"
                    + "\n"
                    + "Regards,";

            mailService.sendMessage(sender, recipient, null, null, String.format(subject, serverName, site),
                    String.format(body, remoteAddress, DATE_FORMAT.format(loginDate)));
        }
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

}
