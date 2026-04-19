package org.jahia.modules.rootloginnotification;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;

import java.util.Dictionary;

@Component(
        immediate = true,
        service = {RootLoginNotificationConfig.class, ManagedService.class},
        property = Constants.SERVICE_PID + "=org.jahia.modules.rootloginnotification"
)
public class RootLoginNotificationConfig implements ManagedService {

    public static final String DEFAULT_SUBJECT =
            "[{server} - {site}] Connection notification to the root account";

    public static final String DEFAULT_BODY =
            "<p>Hi,</p>"
            + "<p>We're sending this email following a successful connection to the root user.</p>"
            + "<p>Connection IP: {ip}<br>Connection time: {time}</p>"
            + "<p>This email is meant to raise awareness about the security of your services"
            + " and to help you to protect them.</p>"
            + "<p>Regards,</p>";

    private String recipient = null;
    private String sender = null;
    private String subject = DEFAULT_SUBJECT;
    private String body = DEFAULT_BODY;

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary == null) {
            return;
        }
        recipient = (String) dictionary.get("recipient");
        sender = (String) dictionary.get("sender");
        final String configSubject = (String) dictionary.get("subject");
        subject = (configSubject != null && !configSubject.isEmpty()) ? configSubject : DEFAULT_SUBJECT;
        final String configBody = (String) dictionary.get("body");
        body = (configBody != null && !configBody.isEmpty()) ? configBody : DEFAULT_BODY;
    }

    /** Returns the configured recipient, or null to fall back to the MailService default. */
    public String getRecipient() {
        return recipient;
    }

    /** Returns the configured sender, or null to fall back to the MailService default. */
    public String getSender() {
        return sender;
    }

    /** Returns the subject template. Supports {server} and {site} tokens. */
    public String getSubject() {
        return subject;
    }

    /** Returns the body template. Supports {ip} and {time} tokens. */
    public String getBody() {
        return body;
    }
}
