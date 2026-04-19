package org.jahia.modules.rootloginnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.regex.Pattern;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("RootLoginNotificationMutations")
@GraphQLDescription("Root Login Notification mutations")
public class RootLoginNotificationMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootLoginNotificationMutationExtension.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private RootLoginNotificationMutationExtension() {
    }

    private static boolean isValidEmail(String email) {
        return email == null || email.isEmpty() || EMAIL_PATTERN.matcher(email).matches();
    }

    @GraphQLField
    @GraphQLName("rootLoginNotificationSaveSettings")
    @GraphQLDescription("Saves the root login notification mail settings")
    @GraphQLRequiresPermission("admin")
    public static Boolean saveSettings(
            @GraphQLName("recipient") @GraphQLDescription("Custom recipient email (optional, leave empty to use MailService default)") String recipient,
            @GraphQLName("sender") @GraphQLDescription("Custom sender email (optional, leave empty to use MailService default)") String sender,
            @GraphQLName("subject") @GraphQLDescription("Subject template — tokens: {server}, {site}") String subject,
            @GraphQLName("body") @GraphQLDescription("Body template — tokens: {ip}, {time}") String body) {
        if (!isValidEmail(recipient)) {
            throw new IllegalArgumentException("Invalid recipient email address: " + recipient);
        }
        if (!isValidEmail(sender)) {
            throw new IllegalArgumentException("Invalid sender email address: " + sender);
        }
        try {
            final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            final Configuration config = configAdmin.getConfiguration("org.jahia.modules.rootloginnotification", null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            if (recipient != null && !recipient.isEmpty()) {
                props.put("recipient", recipient);
            } else {
                props.remove("recipient");
            }
            if (sender != null && !sender.isEmpty()) {
                props.put("sender", sender);
            } else {
                props.remove("sender");
            }
            if (subject != null && !subject.isEmpty()) {
                props.put("subject", subject);
            }
            if (body != null && !body.isEmpty()) {
                props.put("body", body);
            }
            config.update(props);
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.error("Failed to save root login notification settings", e);
            return Boolean.FALSE;
        }
    }
}
