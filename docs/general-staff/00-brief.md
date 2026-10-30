# TripleA AI General Staff — Project Brief (handoff document)

> This is the originating brief for the General Staff initiative, kept verbatim so
> every future session starts from the same mission statement. The reconnaissance
> report that answers §14 is [01-reconnaissance-report.md](01-reconnaissance-report.md);
> the resulting architecture and implementation plan is
> [02-architecture-and-implementation-plan.md](02-architecture-and-implementation-plan.md).

## 1. Vision

Build an AI-powered strategic command interface for TripleA, the open-source Java turn-based
strategy game engine inspired by Axis & Allies.

Repository: https://github.com/triplea-game/triplea

The goal is to dramatically reduce the time required to play large TripleA games while
preserving high-level strategic decision-making.

The player acts as Supreme Commander, issuing natural-language strategic directives. An
LLM-powered General Staff interprets those directives, generates operational plans, evaluates
possible moves using the actual TripleA engine, recommends optimal actions, and executes
approved orders.

Think: "Stockfish for Axis & Allies" combined with a conversational military command staff.

The human decides WHAT to accomplish. The AI determines HOW to accomplish it.

## 2. Example Gameplay

Scenario: TripleA 1940 large/world map. Player controls Germany and potentially coordinates
with Italy.

Example directive:

> "Prioritize an offensive against the Soviet Union toward the Caucasus industrial base.
> Weaken France so Italy can capture Paris on its turn and receive the intended economic
> benefit. Conservatively attack British naval assets with aircraft when favorable. Maintain
> limited pressure in North Africa. Preserve forces for the eastern campaign."

The system should:

1. Interpret strategic intent.
2. Read the actual board state and game rules.
3. Identify objectives and constraints.
4. Generate legal candidate move sequences.
5. Evaluate tactical and strategic outcomes.
6. Simulate alternative continuations.
7. Recommend an operational plan.
8. Explain tradeoffs and risks conversationally.
9. Request approval where appropriate.
10. Execute approved moves through TripleA.
11. Report results and adapt subsequent plans.

The player should be able to complete complex turns in minutes rather than manually moving
dozens of units.

## 3. Primary Prototype: Operation Paris

Initial proof of concept:

> "Ensure Italy captures Paris rather than Germany. Germany should facilitate the Italian
> conquest while preserving forces for an eastern offensive."

The planner must:

- Identify the exact map/version and turn order.
- Verify national objectives and economic rewards.
- Confirm whether the proposed strategy is legal and feasible.
- Prevent Germany from capturing Paris.
- Find legal German preparatory moves.
- Identify Italian ground units capable of reaching Paris.
- Account for movement, territory ownership, combat, and intervening Allied turns.
- Evaluate attack probabilities and expected casualties.
- Consider opponent responses.
- Compare immediate versus delayed Italian conquest.
- Produce executable unit-level orders.
- Explain why the recommended plan best satisfies the commander's intent.

Important: Do NOT assume Italy receives a special Paris bonus in every 1940 ruleset. Verify the
actual TripleA map XML and game configuration.

Do NOT fabricate unit positions, legal paths, combat odds, or simulation results.

If the desired outcome is impossible, explain why and recommend the closest feasible alternative.

## 4. Proposed Architecture

```
Human Supreme Commander
    |
    v
Conversational LLM General Staff
    |
    v
Strategic Intent Parser
    |
    v
Structured Objectives and Constraints
    |
    v
Operational Planning Engine
    |
    +--> TripleA Game State Reader
    +--> Legal Action Generator
    +--> Battle Probability Calculator
    +--> Scenario / Continuation Simulator
    +--> Plan Scoring and Ranking
    |
    v
Ranked Operational Plans
    |
    v
LLM Explanation and Human Approval
    |
    v
TripleA Action Executor
    |
    v
Updated Game State and After-Action Report
```

Architectural principle: the LLM handles language, strategic intent, explanations, and
orchestration. Deterministic software handles game rules, legal moves, combat resolution,
probability calculations, and state mutation. Never rely on an LLM alone to determine move
legality or combat odds.

## 5. Technology Preferences

Development environment: macOS, Homebrew, Ollama already installed, ChromaDB already installed.

Preferred technologies: Java for integration with TripleA; Python for experimental
planning/search if useful; Ollama for local LLM inference; structured JSON for communication
between components; web-based command interface or embedded TripleA UI.

Avoid unnecessary infrastructure. Prefer a modular extension to TripleA rather than a complete
rewrite or an invasive fork. Investigate existing TripleA AI implementations, game-state
interfaces, delegates, battle calculators, and testing infrastructure before designing
replacements. Do not assume any particular API is publicly accessible until verified in the
repository.

## 6. Core Data Structures (provisional)

- **StrategicDirective**: objective, priority, hard_constraints, soft_constraints,
  risk_tolerance, planning_horizon, delegated_theaters, approval_requirements
- **GameObservation**: current_player, turn_phase, territories, territory_ownership, units,
  unit_capabilities, economic_resources, legal_actions, national_objectives,
  diplomatic_relationships, relevant_rule_configuration
- **OperationalPlan**: plan_id, strategic_objectives, preconditions, ordered_actions,
  contingent_actions, expected_outcomes, simulation_results, confidence_estimates,
  constraint_violations, explanation
- **SimulationResult**: candidate_plan, number_of_trials, assumptions, opponent_policy,
  objective_success_rate, expected_casualties, expected_economic_outcome,
  resulting_position_score, uncertainty, failure_modes

All data structures are provisional and should be adapted to the actual TripleA architecture.

## 7. Planning and Optimization

Hierarchical planner: Level 1 strategic intent (capture Paris with Italy, preserve German
armor, prioritize Caucasus, avoid costly naval engagements); Level 2 operational objectives
(open an Italian approach to Paris, position forces for an eastern offensive, preserve
aircraft); Level 3 tactical actions (purchase, move, select attacks, allocate aircraft, resolve
casualties, reposition survivors).

Generate multiple candidate plans and evaluate them against the actual game engine. Potential
techniques: heuristic search, beam search, Monte Carlo simulation, MCTS, existing TripleA AI
heuristics, multi-objective optimization, opponent-response modeling.

Do not begin by implementing a massive general-purpose search engine. First establish reliable
game-state extraction, legal action validation, and isolated simulation.

## 8. Scoring

Prioritized objectives rather than a single material score. For Operation Paris: primary =
maximize probability that Italy captures Paris within the planning horizon; hard constraint =
Germany must not capture Paris; secondary = preserve German strength, minimize casualties,
maximize economic advantage, preserve eastern offensive capability, minimize exposure to Allied
counterattack. Prefer lexicographic or constrained optimization. Evaluate stochastic combat with
repeated trials and report uncertainty. Distinguish computed probabilities, heuristic estimates,
assumptions, and unknowns.

## 9. Human Interaction

Behave like a competent military advisory staff, not a generic chatbot. Validate orders, execute
authorized moves, stop at approval boundaries, report deviations, request clarification on
conflicting objectives, never silently violate hard constraints.

Modes: A. Full delegation; B. Copilot (AI recommends, human executes/approves); C. Hybrid
(human controls selected theaters or major battles). Hybrid is the preferred long-term design.

## 10. Persistent Strategic Doctrine

Standing instructions across turns (e.g. "Preserve German strategic bombers. Prioritize Soviet
industrial centers. Do not launch attacks with less than a 70% estimated success probability
without approval."). Store doctrine, campaign objectives, risk preferences, delegation
boundaries, current operational commitments; reconcile against changing conditions. ChromaDB
only if it provides a concrete advantage.

## 11. Safety and Correctness

Treat the game engine as authoritative. Validate every proposed action. Never modify the live
game during hypothetical simulations; use isolated state copies. Preserve game-state
consistency, respect turn order and phase boundaries, handle stochastic combat correctly, never
invent simulation outcomes, human approval gate before irreversible actions, log executed moves
and results, support rollback through verified save/reload where feasible, test unusual rules.

## 12. Development Milestones

- Phase 1 — Repository Reconnaissance (architecture report with file paths and integration
  recommendations)
- Phase 2 — Read-Only Game Bridge (load a real game or save, export structured observations)
- Phase 3 — Conversational Advisor (Ollama; natural-language directives → validated structured
  objectives; explain the position)
- Phase 4 — Tactical Evaluation (battle probability evaluation, legal candidate action
  generation, compare alternatives)
- Phase 5 — Operation Paris Planner (multi-turn German/Italian plans, feasibility, ranking by
  real simulation)
- Phase 6 — Approved Execution (execute validated plans with approval; pause, resume,
  after-action reporting)
- Phase 7 — Full General Staff (persistent doctrine, multi-theater planning, delegation,
  opponent modeling)

## 13. Performance Goals

Reduce gameplay time: fast planning, minimal manual unit management, useful explanations,
reliable execution, controllable simulation budgets. Fast heuristic recommendations by default;
deeper analysis on demand.

## 14. Immediate Coding Agent Assignment

Start with reconnaissance, not implementation: inspect the repo; identify structure, build, AI
architecture; locate interfaces for reading state, enumerating/validating legal moves, AI
decision-making, combat resolution and odds, save-game serialization, state cloning/simulation;
determine the least invasive way to build an external General Staff; assess Java-native vs
Java/Python bridge; identify how the 1940 maps encode objectives, movement, turn order, victory;
propose a minimal proof of concept; produce a concrete implementation plan with file paths,
obstacles, tests, milestones; no substantial repository modifications until the report and
architecture are complete; clearly distinguish verified findings from proposals.

## 15. Ultimate Success Criterion

A human can issue: "Germany should concentrate on the Caucasus, facilitate an Italian capture
of Paris, preserve the Luftwaffe, and conduct only favorable naval operations." The General
Staff understands the command, reads the actual board state, develops legal candidate plans,
simulates plausible outcomes, explains its recommendation, executes authorized actions, and
adapts to combat results and opponent moves. The experience should feel like commanding a war
through an intelligent General Staff rather than manually moving hundreds of pieces.
