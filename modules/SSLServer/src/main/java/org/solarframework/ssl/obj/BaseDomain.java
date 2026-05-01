package org.solarframework.ssl.obj;

import org.springframework.http.HttpHeaders;

public abstract class BaseDomain {
    protected transient HttpHeaders Headers = new HttpHeaders();
    private final String IP;
    private final String Name;
    private final String Path;

    public String getIP() {
        return IP;
    }
    public String getName() {
        return Name;
    }
    public String getPath() {
        return Path;
    }
    public abstract String getHost();

    public HttpHeaders getHeaders() {
        return Headers;
    }

    protected BaseDomain(String ip, String name, String path) {
        this.Name = name.replace("*.", "");
        this.IP = ip;
        this.Path = path;
    }
}