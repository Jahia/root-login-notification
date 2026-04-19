import {registry} from '@jahia/ui-extender';
import {RootLoginNotificationAdmin} from './RootLoginNotification';
import React from 'react';

export default () => {
    console.debug('%c root-login-notification: activation in progress', 'color: #006633');
    registry.add('adminRoute', 'rootLoginNotification', {
        targets: ['administration-server-configuration:20'],
        requiredPermission: 'admin',
        label: 'root-login-notification:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(RootLoginNotificationAdmin)
    });
};
