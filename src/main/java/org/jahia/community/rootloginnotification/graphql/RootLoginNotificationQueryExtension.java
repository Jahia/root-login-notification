package org.jahia.community.rootloginnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Root login notification queries")
public class RootLoginNotificationQueryExtension {

    private RootLoginNotificationQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("rootLoginNotification")
    @GraphQLDescription("Root login notification query namespace")
    public static RootLoginNotificationQuery rootLoginNotification() {
        return new RootLoginNotificationQuery();
    }
}
