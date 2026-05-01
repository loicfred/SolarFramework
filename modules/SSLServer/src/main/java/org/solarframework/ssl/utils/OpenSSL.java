package org.solarframework.ssl.utils;

import net.lingala.zip4j.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.StandardCopyOption;

import static org.solarframework.core.util.FileUtils.downloadFileFromURL;
import static org.solarframework.core.util.TerminalUtils.runCommandNoPrint;

public class OpenSSL {
    private static final Logger log = LoggerFactory.getLogger(OpenSSL.class);
    private static final String OPENSSL_DOWNLOAD = "https://raw.githubusercontent.com/loicfred/AstralCore-Framework/master/dl-packages/openssl.zip";

    public static boolean IsDownloaded() {
        try {
            runCommandNoPrint(OPENSSLPATH, "-help");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean Download(){
        try {
            File mkcert = new File(OPENSSLPATH);
            if (mkcert.exists()) return true;
            downloadFileFromURL(OPENSSL_DOWNLOAD, "./openssl.zip", StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.error("Failed to download OpenSSL: {}", e.getMessage());
            return false;
        }
    }
    public static boolean Install() {
        try (ZipFile zipFile = new ZipFile("openssl.zip")) {
            zipFile.extractAll("./bin");
            log.info("OpenSSL successfully installed.");
            return new File("./openssl.zip").delete();
        } catch (Exception e) {
            log.error("Failed to installed OpenSSL: {}", e.getMessage());
            return false;
        }
    }

    protected static void MakeCertsSSL() {
        try {
            if (!OpenSSL.IsDownloaded()) if (OpenSSL.Download()) OpenSSL.Install();
            runCommandNoPrint(
                    OPENSSLPATH, "pkcs12", "-export",
                    "-in", "./WAMP/certs/wampdomains.pem",
                    "-inkey", "./WAMP/certs/wampdomains-key.pem",
                    "-out", "WAMP/certs/wampdomains.p12",
                    "-name", "wildcard",
                    "-password", "pass:password"
            );
            log.info("Successfully mapped SSL certificate from PEM to P12.");
        } catch (Exception e) {
            log.error("Failed to map SSL certificate from PEM to P12: {}", e.getMessage());
        }
    }


    protected static final String OPENSSLPATH = getOpenSSLPath();
    private static String getOpenSSLPath() {
        return "./bin/openssl/bin/openssl.exe";
    }
}
