package org.solarframework.proxyserver.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.solarframework.core.util.TerminalUtils.*;
import static org.solarframework.core.util.FileUtils.*;

public class MKCert {
    private static final Logger log = LoggerFactory.getLogger(MKCert.class);
    private static final String MKCERT_DOWNLOAD = "https://github.com/FiloSottile/mkcert/releases/download/v1.4.4/";

    public static boolean IsDownloaded() {
        try {
            runCommandNoPrint(MKCERTPATH, "-help");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean Download(){
        try {
            File mkcert = new File(MKCERTPATH);
            if (mkcert.exists()) return true;
            downloadFileFromURL(MKCERT_DOWNLOAD + MKCERTPATH.split("/")[3], MKCERTPATH, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.error("Failed to download MKCert: {}", e.getMessage());
            return false;
        }
    }
    public static boolean Install() {
        try {
            runCommandNoPrint(MKCERTPATH, "-install");
            log.info("MKCert successfully installed.");
            return true;
        } catch (Exception e) {
            log.error("Failed to install MKCert: {}", e.getMessage());
            return false;
        }
    }

    public static void GenerateCertificateFor(List<String> domains) throws Exception {
        Files.createDirectories(Path.of("./WAMP/certs"));
        List<String> cmd = new ArrayList<>();
        cmd.add(0, "-cert-file");
        cmd.add(1, "./WAMP/certs/wampdomains.pem");
        cmd.add(2, "-key-file");
        cmd.add(3, "./WAMP/certs/wampdomains-key.pem");
        cmd.addAll(domains);
        runCommandNoPrint(MKCERTPATH, cmd);
        OpenSSL.MakeCertsSSL();
    }
    public static void GenerateCertificateFor(String cmd) throws Exception {
        if (!MKCert.IsDownloaded()) if (MKCert.Download()) MKCert.Install();
        GenerateCertificateFor(List.of(cmd));
    }


    protected static final String MKCERTPATH = getMKCertPath();
    private static String getMKCertPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        return switch (os) {
            case "windows", "windows 10", "windows 11" -> switch (arch) {
                case "amd64", "x86_64" -> "./bin/mkcert/mkcert-v1.4.4-windows-amd64.exe";
                case "arm64" -> "./bin/mkcert/mkcert-v1.4.4-windows-arm64.exe";
                default -> throw new RuntimeException("Unsupported Windows CPU: " + arch);
            };
            case "mac", "darwin", "mac os x" -> switch (arch) {
                case "amd64", "x86_64" -> "./bin/mkcert/mkcert-v1.4.4-darwin-amd64";
                case "arm64" -> "./bin/mkcert/mkcert-v1.4.4-darwin-arm64";
                default -> throw new RuntimeException("Unsupported macOS CPU: " + arch);
            };
            case "linux" -> switch (arch) {
                case "amd64", "x86_64" -> "./bin/mkcert/mkcert-v1.4.4-linux-amd64";
                case "arm" -> "./bin/mkcert/mkcert-v1.4.4-linux-arm";
                case "aarch64", "arm64" -> "./bin/mkcert/mkcert-v1.4.4-linux-arm64";
                default -> throw new RuntimeException("Unsupported Linux CPU: " + arch);
            };
            default -> throw new RuntimeException("Unsupported OS: " + os);
        };
    }
}