package org.jahia.community.rootloginnotification;

import org.junit.Test;

import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link RootLoginNotificationConfig} — the file-backed OSGi config value holder
 * (F6, U8). Pure POJO, no mocks beyond a {@link Hashtable} dictionary.
 */
public class RootLoginNotificationConfigTest {

    // ── S20 — hard-coded defaults before updated() is ever called ────────────────────

    @Test
    public void returnsHardCodedDefaultsBeforeUpdated() {
        RootLoginNotificationConfig config = new RootLoginNotificationConfig();

        assertThat(config.getRecipient()).isNull();
        assertThat(config.getSender()).isNull();
        assertThat(config.getSubject()).isEqualTo(RootLoginNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(RootLoginNotificationConfig.DEFAULT_BODY);
    }

    // ── S19 (a) — all four keys supplied → getters return exact values ───────────────

    @Test
    public void updatedWithAllKeysReturnsExactValues() throws Exception {
        RootLoginNotificationConfig config = new RootLoginNotificationConfig();
        Hashtable<String, Object> dict = new Hashtable<>();
        dict.put("recipient", "to@example.com");
        dict.put("sender", "from@example.com");
        dict.put("subject", "Custom subject {server}");
        dict.put("body", "Custom body {ip}");

        config.updated(dict);

        assertThat(config.getRecipient()).isEqualTo("to@example.com");
        assertThat(config.getSender()).isEqualTo("from@example.com");
        assertThat(config.getSubject()).isEqualTo("Custom subject {server}");
        assertThat(config.getBody()).isEqualTo("Custom body {ip}");
    }

    // ── S19 (b) — empty subject/body fall back to defaults ───────────────────────────

    @Test
    public void updatedWithEmptySubjectAndBodyFallsBackToDefaults() throws Exception {
        RootLoginNotificationConfig config = new RootLoginNotificationConfig();
        Hashtable<String, Object> dict = new Hashtable<>();
        dict.put("recipient", "to@example.com");
        dict.put("sender", "from@example.com");
        dict.put("subject", "");
        dict.put("body", "");

        config.updated(dict);

        assertThat(config.getSubject()).isEqualTo(RootLoginNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(RootLoginNotificationConfig.DEFAULT_BODY);
        assertThat(config.getRecipient()).isEqualTo("to@example.com");
    }

    // ── S19 (c) — absent subject/body → defaults; absent recipient/sender → null ─────

    @Test
    public void updatedWithAbsentKeysUsesDefaultsAndNulls() throws Exception {
        RootLoginNotificationConfig config = new RootLoginNotificationConfig();
        Hashtable<String, Object> dict = new Hashtable<>(); // empty dictionary

        config.updated(dict);

        assertThat(config.getSubject()).isEqualTo(RootLoginNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(RootLoginNotificationConfig.DEFAULT_BODY);
        assertThat(config.getRecipient()).isNull(); // falls back to MailService default downstream
        assertThat(config.getSender()).isNull();
    }

    // ── S19 (d) — null dictionary is a no-op (no NPE, values unchanged) ──────────────

    @Test
    public void updatedWithNullDictionaryIsNoOp() {
        RootLoginNotificationConfig config = new RootLoginNotificationConfig();

        assertThatCode(() -> config.updated(null)).doesNotThrowAnyException();

        assertThat(config.getRecipient()).isNull();
        assertThat(config.getSender()).isNull();
        assertThat(config.getSubject()).isEqualTo(RootLoginNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(RootLoginNotificationConfig.DEFAULT_BODY);
    }
}
