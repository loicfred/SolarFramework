package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.PhaseType;

/**
 * Round robin, which is a group phase of one group. A named subclass rather than a constructor
 * argument so {@link java.util.ServiceLoader} can build it.
 */
public class RoundRobinEngine extends GroupEngine {

    public RoundRobinEngine() { super(PhaseType.ROUND_ROBIN); }
}
