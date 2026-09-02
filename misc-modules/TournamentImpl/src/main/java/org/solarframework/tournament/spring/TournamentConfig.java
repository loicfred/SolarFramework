package org.solarframework.tournament.spring;

import org.solarframework.tournament.api.IBracketRenderer;
import org.solarframework.tournament.api.IPhaseEngine;
import org.solarframework.tournament.api.PhaseEngines;
import org.solarframework.tournament.api.TournamentRegistry;
import org.solarframework.tournament.impl.render.BracketRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Spring way in, for a host that wants one.
 * <p>Nothing in this module is a Spring bean, because the stack also has to run where there is no application
 * context at all - {@code new BracketRenderer()} and the {@link PhaseEngines} ServiceLoader are the whole of the
 * setup, which is how a plugin carrying this module uses it. This class is the other half: a host that
 * component-scans this package gets the renderer as a bean without doing anything else.
 */
@Configuration
public class TournamentConfig {

    /** {@link BracketRenderer} assigns {@link TournamentRegistry#SolarBrackets} as it is built, so the bean and the static hand-off come from the one line. */
    @Bean
    public IBracketRenderer solarBrackets() {
        return new BracketRenderer();
    }

    /** Registers every {@link IPhaseEngine} the host declares as a bean, which is how an application swaps in its own pairing rules. A host declaring none keeps the five the ServiceLoader finds. Runs once the singletons are up rather than from a constructor, so an engine bean of the host's own is fully built before it is handed over. */
    @Bean
    public SmartInitializingSingleton solarPhaseEngines(ObjectProvider<IPhaseEngine> engines) {
        return () -> engines.forEach(PhaseEngines::register);
    }
}
