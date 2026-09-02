package org.solarframework.tournament.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Engine lookup by phase type. Engines hold no state, so one instance each is enough.
 * <p>Lives here rather than with the engines because the entities need it and they are API types.
 * The engines themselves are found through {@link ServiceLoader} on first use — an implementation
 * module declares them in {@code META-INF/services/org.solarframework.tournament.api.IPhaseEngine},
 * so a host wires up nothing.
 */
public final class PhaseEngines {

    private static final Map<PhaseType, IPhaseEngine> ENGINES = new EnumMap<>(PhaseType.class);
    private static boolean loaded;

    private PhaseEngines() {}

    /** Replaces the engine for a type — lets a consumer swap in its own pairing rules. */
    public static synchronized void register(IPhaseEngine engine) {
        loaded = true;
        ENGINES.put(engine.type(), engine);
    }

    public static synchronized IPhaseEngine of(PhaseType type) {
        if (!loaded) load();
        IPhaseEngine e = ENGINES.get(type);
        if (e == null) throw TournamentException.of("No engine registered for phase type %s", type);
        return e;
    }

    private static void load() {
        loaded = true;
        for (IPhaseEngine engine : ServiceLoader.load(IPhaseEngine.class)) ENGINES.putIfAbsent(engine.type(), engine);
    }
}
