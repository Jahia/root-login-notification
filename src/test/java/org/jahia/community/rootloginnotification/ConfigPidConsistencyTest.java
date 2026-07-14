package org.jahia.community.rootloginnotification;

import org.junit.Test;

import java.io.File;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3 config-PID mismatch guard (S38) — <b>regression guard (GREEN since the Stage-7 fix).</b>
 *
 * <p>Felix FileInstall delivers a shipped default {@code <PID>.cfg} to the OSGi service registered
 * under that same PID. The service/mutation consume the PID
 * {@link RootLoginNotificationConfig#PID} ({@code RootLoginNotificationConfig} {@code service.pid},
 * {@code RootLoginNotificationMutation} ConfigurationAdmin lookup). Before Stage 7 the shipped file
 * was named {@code org.jahia.modules.rootloginnotification.cfg} — so the default never reached
 * {@code updated()} and the shipped {@code subject}/{@code body} were silently ignored (the
 * Java-hardcoded defaults masked the bug).
 *
 * <p>This is a deterministic build/resource assertion — no container, no test ordering. It reads the
 * shipped {@code .cfg} basename and asserts it equals the consumed PID. Stage 7 {@code git mv}'d the
 * file to {@code org.jahia.community.rootloginnotification.cfg}, so this now passes and stands as the
 * regression guard preventing the PID drift from recurring.
 */
public class ConfigPidConsistencyTest {

    /** The PID the ManagedService + mutation actually consume. */
    private static final String CONSUMED_PID = RootLoginNotificationConfig.PID;

    private static final String CONFIG_DIR = "src/main/resources/META-INF/configurations";

    @Test
    public void shippedDefaultCfgBasenameEqualsConsumedPid() {
        File baseDir = new File(System.getProperty("basedir", "."));
        File configDir = new File(baseDir, CONFIG_DIR);
        assertThat(configDir)
                .as("config directory %s must exist", configDir.getPath())
                .isDirectory();

        File[] cfgFiles = configDir.listFiles((dir, name) -> name.endsWith(".cfg"));
        assertThat(Objects.requireNonNull(cfgFiles, "listFiles returned null"))
                .as("exactly one shipped default .cfg is expected")
                .hasSize(1);

        String basename = cfgFiles[0].getName().replaceFirst("\\.cfg$", "");

        // EXPECTED-RED until Stage 7 renames the file. FileInstall keys the delivered config by this
        // basename, so it MUST match the PID the service registers under, or the default is dropped.
        assertThat(basename)
                .as("shipped .cfg basename must equal the consumed OSGi PID so FileInstall "
                        + "delivers the default to the service (D3 config-PID mismatch)")
                .isEqualTo(CONSUMED_PID);
    }
}
