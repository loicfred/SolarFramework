package org.solarframework.discord.interaction;

import org.solarframework.discord.core.CMD;

public class Extension extends CMD {

    private final String hi;
    public Extension(String hi) {
        this.hi = hi;
    }

    public String getHi() {
        return hi;
    }
}
