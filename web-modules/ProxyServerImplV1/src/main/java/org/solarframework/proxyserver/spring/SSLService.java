package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.Domain;
import org.solarframework.proxyserver.obj.Subdomain;
import org.solarframework.proxyserver.utils.MKCert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SSLService {
    private static final Logger log = LoggerFactory.getLogger(SSLService.class);

    private final String COMMENT_TAG = "# Added by SolarFramework-ProxyManager";
    private final List<Domain> domains;


    public List<String> getAbsoluteHosts() {
        try {
            return Files.readAllLines(getHostPath()).stream().filter(line -> line.contains(COMMENT_TAG)).map(s -> s.split(" ")[1]).collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
    public List<String> getHosts() {
        return getDomains().stream().flatMap(domain -> domain.getHosts().stream()).toList();
    }

    public void addHostEntry(String ip, String hosts) throws IOException {
        addHostEntry(ip, List.of(hosts));
    }
    public void addHostEntry(String ip, List<String> hosts) throws IOException {
        Path hostsPath = getHostPath();
        for (String host : hosts) {
            if (Files.readAllLines(hostsPath).stream().noneMatch(line -> line.contains(host))) {
                List<String> lines = Files.readAllLines(hostsPath);
                String prefix = lines.getLast().isBlank() ? "" : "\n";
                Files.write(hostsPath, (prefix + ip + " " + host + " " + COMMENT_TAG).getBytes(), StandardOpenOption.APPEND);
                log.info("Host domain [{}] added to [C:/Windows/System32/drivers/etc/hosts]", host);
            }
        }
    }

    public void removeHostEntry(String hosts) throws IOException {
        removeHostEntry(List.of(hosts));
    }
    public void removeHostEntry(List<String> hosts) throws IOException {
        Path hostsPath = getHostPath();
        for (String host : hosts) {
            List<String> keptLines = Files.readAllLines(hostsPath).stream().filter(line -> !(line.contains(host) && line.contains(COMMENT_TAG))).collect(Collectors.toList());
            Files.write(hostsPath, keptLines, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Host domain [{}] removed from [C:/Windows/System32/drivers/etc/hosts]", host);
        }
    }
    public void clearHostEntries() throws IOException {
        Path hostsPath = getHostPath();
        List<String> keptLines = Files.readAllLines(hostsPath).stream().filter(line -> !line.contains(COMMENT_TAG)).collect(Collectors.toList());
        Files.write(hostsPath, keptLines, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("Deleted all program-made hosts from [C:/Windows/System32/drivers/etc/hosts]");
    }


    public List<Domain> getDomains() {
        return domains;
    }
    public String getPath(String subdomain) {
        return getDomains().stream().map(dom -> dom.getPath(subdomain)).filter(Objects::nonNull).findFirst().orElse(null);
    }
    public HttpHeaders getHeaders(String subdomain) {
        HttpHeaders HttpHeaders = getDomains().stream().map(dom -> dom.getHeaders(subdomain)).filter(Objects::nonNull).findFirst().orElse(null);
        return HttpHeaders != null ? org.springframework.http.HttpHeaders.copyOf(HttpHeaders) : null;
    }

    protected SSLService(List<Domain> domains, boolean regenCerts) throws Exception {
        this.domains = domains;
        if (regenCerts) {
            clearHostEntries();
            for (Domain dom : domains) addHostEntry(dom.getIp(), dom.getHosts());
            MKCert.GenerateCertificateFor(getHosts());
        }
        List<String> trueHosts = getAbsoluteHosts();
        for (Domain domain : getDomains()) {
            if (trueHosts.contains(domain.getName())) {
                if (!domain.getPath().startsWith("http")) Files.createDirectories(Path.of(domain.getPath()));
                log.info("Loaded domain: {}", domain.getName());
            } else log.error("Failed to load domain: {}", domain.getName());

            for (Subdomain sub : domain.getSubdomains()) {
                if (trueHosts.contains(sub.getHost())) {
                    if (!sub.getPath().startsWith("http")) Files.createDirectories(Path.of(sub.getPath()));
                    log.info("└ Loaded subdomain: {}", sub.getHost());
                } else log.error("└ Failed to load subdomain: {}", sub.getHost());
            }
        }
    }


    private static Path getHostPath() {
        return Paths.get(System.getProperty("os.name").toLowerCase().contains("win") ? "C:\\Windows\\System32\\drivers\\etc\\hosts" : "/etc/hosts");
    }
}
