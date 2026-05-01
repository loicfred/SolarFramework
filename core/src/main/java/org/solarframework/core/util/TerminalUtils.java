package org.solarframework.core.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TerminalUtils {
    public static void runCommand(String name, List<String> commands) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(name);
        command.addAll(commands);
        runCommand(command.toArray(new String[0]));
    }
    public static void runCommandNoPrint(String name, List<String> commands) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(name);
        command.addAll(commands);
        runCommandNoPrint(command.toArray(new String[0]));
    }


    public static void runCommand(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        System.out.println("Exited with: " + process.waitFor());
    }
    public static void runCommandNoPrint(String... command) throws Exception {
        new ProcessBuilder(command).redirectErrorStream(true).start().waitFor();
    }
}
