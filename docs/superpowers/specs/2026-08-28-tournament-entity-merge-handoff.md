# Handoff — Tournament entity merge (28 August 2026)

State: **done and green** — `TournamentAPI` + `TournamentImpl` build with all **72 tests passing**.
Not committed. Read this before touching the Tournament modules again.

## What changed and why

The seven entities existed **twice**: an abstract `IMatch`/`IPhase`/… in TournamentAPI carrying the JPA
mapping, and a concrete `Match`/`Phase`/… in TournamentImpl carrying the behaviour. They are now
**one concrete entity each, in TournamentAPI** (`org.solarframework.tournament.obj`):

`Match` · `MatchGame` · `Participant` · `ParticipantMember` · `Phase` · `Standing` · `Tournament`

The seven `I*` abstract classes are gone. This works for the same reason the AI entities can live in
AIAPI: these classes reach outward only through **API-level types** — `IPhaseEngine`,
`TournamentRegistry`, `DatabaseRegistry` — never into TournamentImpl.

Deleted along with the split: `@Inheritance(SINGLE_TABLE)`, `@DiscriminatorFormula("'0'")` and
`@DiscriminatorValue("0")`. That apparatus existed **only** to let an abstract parent and a concrete
child share one table. One class needs none of it.

### Also moved to TournamentAPI

| Was | Now | Why it could move |
|---|---|---|
| `impl/StandingsCalculator` | `tournament/util/StandingsCalculator` | pure algorithm over API types |
| `impl/seed/Seeder` | `tournament/util/Seeder` | same |
| `impl/seed/Brackets` | `tournament/util/Brackets` | same (Seeder depends on it) |
| `impl/engine/PhaseEngines` | `tournament/api/PhaseEngines` | the *lookup* is API; the engines are not |

No interfaces were invented for these — they are algorithms, not swappable strategies.

### Engine lookup: ServiceLoader

`api/PhaseEngines.of(type)` keeps an `EnumMap` and, on first use, fills it from
`ServiceLoader.load(IPhaseEngine.class)`. TournamentImpl declares its engines in

```
misc-modules/TournamentImpl/src/main/resources/META-INF/services/org.solarframework.tournament.api.IPhaseEngine
```

so a host wires up nothing — behaviour is identical to the old static block. `register(...)` still
lets a consumer swap in its own pairing rules.

`GroupEngine` was registered twice with a constructor argument (`GROUP` and `ROUND_ROBIN`), which
ServiceLoader cannot do, so **`RoundRobinEngine extends GroupEngine`** was added as a no-arg subclass.

### TournamentImpl now holds only

`engine/` (AbstractPhaseEngine + the 5 engines) and `render/` (BracketRenderer + painters). Nothing else.

## Things a future session must know

- **The merge was scripted**, then compiled and test-driven to green. Four classes of fallout were
  fixed by hand and are the things to re-check if anything looks odd:
  1. `@Override` on ~84 methods that no longer override anything (parent's abstract declaration is
     gone) — all stripped. **Some legitimate overrides of `DatabaseObject` methods lost their
     `@Override` too.** Harmless, but re-adding them where real would be tidier.
  2. Child constructors that merely called `super(...)` were dropped; the parent's equivalents were
     `protected` and had to be widened to `public` (the engines and tests construct them).
  3. `Participant.checkIn()` existed **concretely** in both — the parent's became the private
     `markCheckedIn()`, which the merged `checkIn()` calls.
  4. Three TournamentImpl tests needed imports for the relocated `Brackets`/`Seeder`/`StandingsCalculator`.
- The script lived at `<scratchpad>/merge.sh` and is **not** in the repo; it was one-shot.
- `IPhaseEngine` and `IBracketRenderer` survive deliberately — both have real or plausible second
  implementations, and `IBracketRenderer` is already a `TournamentRegistry` hand-off.

## Worth doing next

- **`AbstractPhaseEngine` vs `IPhaseEngine`** — Loïc raised this and it was not done. The abstract
  class could likely be folded into the interface as `default` methods, exactly as
  `IConversation` was in the AI module before it too was collapsed. Check what state
  `AbstractPhaseEngine` holds first; if it is stateless, the merge is clean and removes a class.
- Re-add `@Override` where it genuinely applies (see fallout 1).
- Consider whether `tournament/obj/convert/*` converters are still all used.

## Related, same session

The AI module was restructured far more heavily (interfaces 8 → 2, `Conversation`/`ChatMessage` are
now `@Entity` in **AIAPI**, structured output moved to native `response_format`). `CLAUDE.md` is
current for that; the spec at `2026-08-28-ai-chatbot-agent-design.md` **predates it and is stale**.
