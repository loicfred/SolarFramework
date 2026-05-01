package org.solarframework.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Scanner;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static void replaceAllStringInDir(File file, String string, String newstring) {
        if (file.getName().contains(string)) {
            File newfile = new File(file.getPath().replaceAll(string, newstring));
            file.renameTo(newfile);
            file = newfile;
        }
        if (file.isDirectory()) {
            if (file.listFiles() != null) {
                for (File f : Objects.requireNonNull(file.listFiles())) {
                    replaceAllStringInDir(f, string, newstring);
                }
            }
        } else if (file.getName().contains(".txt")) {
            try {
                Scanner scan = new Scanner(file, StandardCharsets.UTF_8);
                String texts = "";
                while (scan.hasNextLine()) {
                    texts = texts + scan.nextLine() + System.lineSeparator();
                }
                scan.close();
                texts = texts.replaceAll(string, newstring);
                FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8);
                PrintWriter pw = new PrintWriter(fw);
                pw.print(texts);
                pw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    public static String getFileSize(byte[] data) {
        return getFileSize(data.length);
    }

    public static void downloadFileFromURL(String url, String targetPath, StandardCopyOption option) throws IOException {
        Path target = Paths.get(targetPath);
        Files.createDirectories(target.getParent());
        try (InputStream in = URI.create(url).toURL().openStream()) {
            log.info("Downloaded {} file from: [{}]", getFileSize(Files.copy(in, target, option)), url);
        } catch (IOException e) {
            log.error("Failed to download file from: {}", url);
        }
    }

}
