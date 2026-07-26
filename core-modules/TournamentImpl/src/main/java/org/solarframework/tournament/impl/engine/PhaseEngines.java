package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.IPhaseEngine;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;

import java.util.EnumMap;
import java.util.Map;

/** Engine lookup by phase type. Engines hold no state, so one instance each is enough. */
public final class PhaseEngines {
    private static final Map<PhaseType, IPhaseEngine> ENGINES = new EnumMap<>(PhaseType.class);

    static {
        register(new SingleEliminationEngine());
        register(new DoubleEliminationEngine());
        register(new GroupEngine(PhaseType.GROUP));
        register(new GroupEngine(PhaseType.ROUND_ROBIN));
        register(new SwissEngine());
    }

    private PhaseEngines() {}

    /** Replaces the engine for a type - lets a consumer swap in its own pairing rules. */
    public static void register(IPhaseEngine engine) { ENGINES.put(engine.type(), engine); }

    public static IPhaseEngine of(PhaseType type) {
        IPhaseEngine e = ENGINES.get(type);
        if (e == null) throw TournamentException.of("No engine registered for phase type %s", type);
        return e;
    }
}
