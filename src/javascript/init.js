import {registry} from '@jahia/ui-extender';
import register from './RootLoginNotification/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'root-login-notification', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('root-login-notification', () => {
                console.debug('%c root-login-notification: i18n namespace loaded', 'color: #006633');
            });
            register();
            console.debug('%c root-login-notification: activation completed', 'color: #006633');
        }
    });
}
