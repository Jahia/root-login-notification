package org.jahia.community.rootloginnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.community.rootloginnotification.RootLoginNotificationConfig;
import org.jahia.osgi.BundleUtils;

@GraphQLName("RootLoginNotificationQuery")
@GraphQLDescription("Root login notification queries")
public class RootLoginNotificationQuery {

    @GraphQLField
    @GraphQLName("settings")
    @GraphQLDescription("Returns the current root login notification mail settings")
    @GraphQLRequiresPermission("rootLoginNotificationAdmin")
    public GqlSettings settings() {
        final RootLoginNotificationConfig config = BundleUtils.getOsgiService(RootLoginNotificationConfig.class, null);
        if (config == null) {
            return GqlSettings.defaults();
        }
        return new GqlSettings(config.getRecipient(), config.getSender(), config.getSubject(), config.getBody());
    }

    @GraphQLName("RootLoginNotificationSettings")
    @GraphQLDescription("Root login notification mail settings")
    public static class GqlSettings {

        private final String recipient;
        private final String sender;
        private final String subject;
        private final String body;

        public GqlSettings(String recipient, String sender, String subject, String body) {
            this.recipient = recipient;
            this.sender = sender;
            this.subject = subject;
            this.body = body;
        }

        public static GqlSettings defaults() {
            return new GqlSettings(null, null,
                    RootLoginNotificationConfig.DEFAULT_SUBJECT,
                    RootLoginNotificationConfig.DEFAULT_BODY);
        }

        @GraphQLField
        @GraphQLName("recipient")
        @GraphQLDescription("Custom recipient email, null if using the MailService default")
        public String getRecipient() {
            return recipient;
        }

        @GraphQLField
        @GraphQLName("sender")
        @GraphQLDescription("Custom sender email, null if using the MailService default")
        public String getSender() {
            return sender;
        }

        @GraphQLField
        @GraphQLName("subject")
        @GraphQLDescription("Subject template. Tokens: {server}, {site}")
        public String getSubject() {
            return subject;
        }

        @GraphQLField
        @GraphQLName("body")
        @GraphQLDescription("Body template. Tokens: {ip}, {time}")
        public String getBody() {
            return body;
        }
    }
}
