package org.jahia.community.rootloginnotification.graphql;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the GraphQL mutation's email-validation rule (S21, U6/F8).
 *
 * Only {@link RootLoginNotificationMutation#isValidEmail(String)} is exercised here — the full
 * {@code saveSettings} path needs {@code ConfigurationAdmin} via the static {@code BundleUtils}
 * and is covered by Cypress (S23–S27).
 */
public class RootLoginNotificationMutationTest {

    @Test
    public void acceptsAWellFormedAddress() {
        assertThat(RootLoginNotificationMutation.isValidEmail("a@b.co")).isTrue();
    }

    @Test
    public void rejectsMalformedAddresses() {
        assertThat(RootLoginNotificationMutation.isValidEmail("not-an-email")).isFalse();
        assertThat(RootLoginNotificationMutation.isValidEmail("a@b")).isFalse();      // no TLD dot
        assertThat(RootLoginNotificationMutation.isValidEmail("a b@c.d")).isFalse();  // whitespace
        assertThat(RootLoginNotificationMutation.isValidEmail("@b.co")).isFalse();    // no local part
    }

    @Test
    public void treatsNullAndEmptyAsValid() {
        // Intentional: blank means "reset to the MailService default", so it must pass validation.
        assertThat(RootLoginNotificationMutation.isValidEmail(null)).isTrue();
        assertThat(RootLoginNotificationMutation.isValidEmail("")).isTrue();
    }
}
