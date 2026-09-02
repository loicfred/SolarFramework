package org.solarframework.core.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessUtils {

    /**
     * Relaunches the jar {@code mainClass} was loaded from, reusing this JVM's own options, then stops this process.
     * Used when code the JVM already loaded has been regenerated on disk and only a fresh start can pick it up.
     */
    public static void restartApplication(Class<?> mainClass, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        command.add("-jar");
        command.add(new File(mainClass.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath());
        if (args != null) command.addAll(Arrays.asList(args));
        new ProcessBuilder(command).inheritIO().start();
        System.exit(0);
    }
}
