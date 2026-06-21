import {gql} from '@apollo/client';

export const GET_SETTINGS = gql`
    query {
        rootLoginNotification {
            settings {
                recipient
                sender
                subject
                body
            }
        }
    }
`;

export const SAVE_SETTINGS = gql`
    mutation RootLoginNotificationSaveSettings($recipient: String, $sender: String, $subject: String, $body: String) {
        rootLoginNotification {
            saveSettings(recipient: $recipient, sender: $sender, subject: $subject, body: $body)
        }
    }
`;
