package org.solarframework.proxyserver.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.proxyserver.obj.BaseDomain;
import org.solarframework.proxyserver.obj.Domain;
import org.solarframework.proxyserver.obj.RateLimitRule;
import org.solarframework.proxyserver.obj.Subdomain;
import org.solarframework.core.util.RateLimiter;
import org.solarframework.certs.MKCert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProxyService {
    protected static ProxyService WAMPSERVER;
    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final String COMMENT_TAG = "# Added by SolarFramework-ProxyManager";
    private final List<Domain> domains;
    private final RateLimiter rateLimiter = new RateLimiter();
    private volatile List<RateLimitRule> rateRules = List.of();


    public List<String> getAbsoluteHosts() {
        try {
            return Files.readAllLines(getHostsFilePath()).stream().filter(line -> line.contains(COMMENT_TAG)).map(s -> s.split(" ")[1]).collect(Collectors.toList());
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
        Path hostsPath = getHostsFilePath();
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
        Path hostsPath = getHostsFilePath();
        for (String host : hosts) {
            List<String> keptLines = Files.readAllLines(hostsPath).stream().filter(line -> !(line.contains(host) && line.contains(COMMENT_TAG))).collect(Collectors.toList());
            Files.write(hostsPath, keptLines, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Host domain [{}] removed from [C:/Windows/System32/drivers/etc/hosts]", host);
        }
    }
    public void clearHostEntries() throws IOException {
        Path hostsPath = getHostsFilePath();
        List<String> keptLines = Files.readAllLines(hostsPath).stream().filter(line -> !line.contains(COMMENT_TAG)).collect(Collectors.toList());
        Files.write(hostsPath, keptLines, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("Deleted all program-made hosts from [C:/Windows/System32/drivers/etc/hosts]");
    }



    public List<Domain> getDomains() {
        return domains;
    }
    public List<Subdomain> getSubDomains() {
        return domains.stream().flatMap(domain -> domain.getSubdomains().stream()).toList();
    }
    public List<BaseDomain<?>> getAllDomains() {
        List<BaseDomain<?>> domains = new ArrayList<>();
        domains.addAll(WAMPSERVER.getDomains());
        domains.addAll(WAMPSERVER.getSubDomains());
        return domains;
    }

    public BaseDomain<?> getDomainOfHost(String host) {
        return getAllDomains().stream().filter(dom -> Objects.equals(dom.getHost(), host)).findFirst().orElse(null);
    }

    /** The registry the running proxy serves from, so a plugin can reach it without being in this package. */
    public static ProxyService current() {
        return WAMPSERVER;
    }

    /** Whether a request was let in at all, how long it must wait when it was, and whether the rule that decided was
     *  contributed by another module's own screen - the edge marks a forwarded request with the last one, so the
     *  application behind it knows the caller was already checked and does not check it again. */
    public record PermitOutcome(boolean allowed, long waitSeconds, boolean contributed) {
        static final PermitOutcome ALLOWED = new PermitOutcome(true, 0, false);
    }
    /** Turns away a caller no rule allows before it can even spend a permit - a flood from an address nobody
     *  allowed should not cost the bucket anything - then spends one for the path it asked for and answers the
     *  seconds it must wait; 0 lets the request through. The first rule in order that claims the path is the one
     *  that applies, so a narrow rule above a wide one wins. */
    public PermitOutcome takePermit(String clientIp, String host, String path) {
        RateLimitRule rule = rateRules.stream().filter(r -> r.matches(host, path)).findFirst().orElse(null);
        if (rule == null) return PermitOutcome.ALLOWED;
        if (!rule.allows(clientIp)) return new PermitOutcome(false, 0, rule.isContributed());
        return new PermitOutcome(true, rule.takePermit(rateLimiter, clientIp), rule.isContributed());
    }


    /** One request refused at the edge - either turned away outright, or made to wait. This module cannot write to
     *  the application's own audit log - it does not depend on it - so it hands the fact to whoever is listening. */
    public interface RefusalListener {
        void onRefusal(String clientIp, String host, String path, long waitSeconds, boolean contributed, boolean blocked);
    }
    private static volatile RefusalListener refusalListener = (clientIp, host, path, waitSeconds, contributed, blocked) -> {};
    /** No-op until the host binds one, the same way {@code ERPEvents} stays quiet until the host sets its dispatcher. */
    public static void setRefusalListener(RefusalListener listener) {
        refusalListener = listener == null ? (clientIp, host, path, waitSeconds, contributed, blocked) -> {} : listener;
    }
    static void notifyRefusal(String clientIp, String host, String path, long waitSeconds, boolean contributed, boolean blocked) {
        refusalListener.onRefusal(clientIp, host, path, waitSeconds, contributed, blocked);
    }
    /** Swaps the rules live, so editing a limit costs neither a proxy restart nor a certificate regeneration. */
    public void reloadRateLimits(List<RateLimitRule> rules) {
        this.rateRules = rules.stream().sorted(Comparator.comparingInt(RateLimitRule::getOrdering)).toList();
        rateLimiter.clear();
    }


    protected ProxyService(List<Domain> domains, List<RateLimitRule> rateRules, boolean regenCerts) throws Exception {
        this.domains = domains;
        reloadRateLimits(rateRules);
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


    private static Path getHostsFilePath() {
        return Paths.get(System.getProperty("os.name").toLowerCase().contains("win") ? "C:\\Windows\\System32\\drivers\\etc\\hosts" : "/etc/hosts");
    }

    public String stripPort(String host) {
        if (host == null) return null;
        int i = host.indexOf(':');
        return (i == -1 ? host : host.substring(0, i)).toLowerCase();
    }
}
