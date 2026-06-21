package org.jahia.modules.rootloginnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Root login notification mutations")
public class RootLoginNotificationMutationExtension {

    private RootLoginNotificationMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("rootLoginNotification")
    @GraphQLDescription("Root login notification mutation namespace")
    public static RootLoginNotificationMutation rootLoginNotification() {
        return new RootLoginNotificationMutation();
    }
}
