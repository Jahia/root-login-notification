package org.jahia.community.rootloginnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.regex.Pattern;

@GraphQLName("RootLoginNotificationMutation")
@GraphQLDescription("Root login notification mutations")
public class RootLoginNotificationMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootLoginNotificationMutation.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // Package-private (not private) so the validation rule can be unit-tested directly without
    // PowerMock; the full saveSettings path needs ConfigurationAdmin and is covered by Cypress.
    static boolean isValidEmail(String email) {
        return email == null || email.isEmpty() || EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Sets {@code key} to {@code value} when non-empty, otherwise removes it so the
     * module falls back to its default for that setting.
     */
    private static void putOrRemove(Dictionary<String, Object> props, String key, String value) {
        if (value != null && !value.isEmpty()) {
            props.put(key, value);
        } else {
            props.remove(key);
        }
    }

    @GraphQLField
    @GraphQLName("saveSettings")
    @GraphQLDescription("Saves the root login notification mail settings")
    @GraphQLRequiresPermission("rootLoginNotificationAdmin")
    public Boolean saveSettings(
            @GraphQLName("recipient") @GraphQLDescription("Custom recipient email (optional, leave empty to use MailService default)") String recipient,
            @GraphQLName("sender") @GraphQLDescription("Custom sender email (optional, leave empty to use MailService default)") String sender,
            @GraphQLName("subject") @GraphQLDescription("Subject template — tokens: {server}, {site}") String subject,
            @GraphQLName("body") @GraphQLDescription("Body template — tokens: {ip}, {time}") String body) {
        if (!isValidEmail(recipient)) {
            throw new IllegalArgumentException("Invalid recipient email address");
        }
        if (!isValidEmail(sender)) {
            throw new IllegalArgumentException("Invalid sender email address");
        }
        try {
            final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            final Configuration config = configAdmin.getConfiguration("org.jahia.community.rootloginnotification", null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            putOrRemove(props, "recipient", recipient);
            putOrRemove(props, "sender", sender);
            putOrRemove(props, "subject", subject);
            putOrRemove(props, "body", body);
            config.update(props);
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.error("Failed to save root login notification settings", e);
            return Boolean.FALSE;
        }
    }
}
