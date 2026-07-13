package org.jahia.community.rootloginnotification;

import org.junit.Test;

import java.io.File;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3 config-PID mismatch guard (S38) — <b>INTENTIONALLY RED until the Stage-7 product fix.</b>
 *
 * <p>Felix FileInstall delivers a shipped default {@code <PID>.cfg} to the OSGi service registered
 * under that same PID. The service/mutation consume the PID
 * {@code org.jahia.community.rootloginnotification}
 * ({@code RootLoginNotificationConfig} {@code service.pid}, {@code RootLoginNotificationMutation}
 * ConfigurationAdmin lookup), but the shipped file is named
 * {@code org.jahia.modules.rootloginnotification.cfg} — so the default never reaches {@code updated()}
 * and the shipped {@code subject}/{@code body} are silently ignored (the Java-hardcoded defaults mask
 * the bug).
 *
 * <p>This is a deterministic build/resource assertion — no container, no test ordering. It reads the
 * shipped {@code .cfg} basename and asserts it equals the consumed PID.
 * <ul>
 *   <li><b>RED today</b> — basename is {@code org.jahia.modules.rootloginnotification}.</li>
 *   <li><b>GREEN after Stage 7</b> — {@code git mv} the file to
 *       {@code org.jahia.community.rootloginnotification.cfg}.</li>
 * </ul>
 * It fails as a normal test assertion (NOT a build/compile break), so the rest of the suite still
 * runs. Do NOT {@code @Ignore} it — its red state is the signal Stage 7 uses to verify the fix.
 */
public class ConfigPidConsistencyTest {

    /** The PID the ManagedService + mutation actually consume. */
    private static final String CONSUMED_PID = "org.jahia.community.rootloginnotification";

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
