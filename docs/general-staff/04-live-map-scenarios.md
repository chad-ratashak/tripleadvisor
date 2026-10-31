# Live-map scenario tests (Global 1940 2nd Edition, Haiku agents)

Date: 2026-10-30. The World War II Global map (maps-list version 15, GitHub `master` zip, 41 MB
extracted) is installed at `~/triplea/downloadedMaps/world_war_ii_global/`; the live 2nd Edition game
file is `map/games/ww2global40_2nd_edition.xml` inside it. This page records what a fleet of Claude
Haiku agents found when they drove the General Staff command line against that file, and what a
second fleet of skeptic agents confirmed or refuted by reading the artifacts. No local language model
was involved: the agents played the model's role themselves where one was needed (writing structured
directives), and every other command is engine-only.

## Method

- One frozen build of the CLI (`./gradlew :general-staff:installDist`) so nothing changed under the
  agents' feet.
- Ten scenario agents (model: Claude Haiku), each with its own output folder, each told to write every
  command's full output to a file and to quote exact lines as evidence. Light scenarios ran in
  parallel; heavy ones (odds, two planner runs, hosted execution, other targets) in groups of two so
  the 8 GB machine stayed honest.
- One skeptic agent per scenario, instructed to default to "refuted" for any claim the files do not
  support, and to "unverifiable" when a file is missing.
- Everything below is a claim the skeptics confirmed unless marked otherwise.

## Facts: live file against the checked-in fixture (confirmed before the fleet ran)

| Fact | Live 2nd Edition | Balanced fixture |
|---|---|---|
| France garrison | 6 inf, 1 art, 1 arm, 1 aaGun, 1 ftr + British 1 art, 1 arm; airfield, major factory | identical |
| France neighbours | Holland Belgium, Normandy Bordeaux, Northern Italy, Southern France, Switzerland, Western Germany | identical |
| Treasuries Germans / Italians / French / British / Russians / Americans | 30 / 10 / 19 / 28 / 37 / 52 | identical |
| Objectives Germans / Italians / British / Americans | 4 / 4 / 1 / 4 | 5 / 5 / 3 / 6 |
| Objective mentioning France | none | none |
| Turn order | Germans, Russians, Japanese, Americans, Chinese, British (+UK_Pacific), Italians, ANZAC, French | Balanced order differs (Americans before Chinese...) as the recon reported |
| aaGun `isInfrastructure` | commented out (a real defending unit) | commented out |
| Rules flags read by the tool | low luck off, national objectives, politics, WW2V3, attacker retreat planes, partial amphibious retreat, capture units on entering, destroy-instead-of-capture, triggered victory, choose AA casualties: all on | same |

## Round 1: what the first fleet found (all confirmed by the skeptics unless noted)

Ten scenarios: map facts, legality, reachability, directives, the API, the calculator baseline, the
planner without and with British reinforcement, hosted execution, and the planner pointed at other
targets. What held up: the live file matches the fixture on every France fact; the engine's verdicts
agreed with the rules on every legality case; the calculator rates on the live file overlap the
fixture's; the planner's ranking order was consistent with its numbers; the hosted game saved before
every movement step and performed every approved order. What did not:

| # | Finding | Severity | Fix (same day) |
|---|---|---|---|
| 1 | `/execute/start` hosted the cached game object: after the hosted turn, `/observe` on the same file described the mutated game, and a second session started at the mutated round | defect | `StaffService.startExecution` hosts a private copy (serialized with delegates); the loaded game is never touched |
| 2 | Orders posted after `/execute/start` could miss the step because the engine starts at once | defect | `/execute/start` accepts `steps` and `run: false`; `/execute/run` starts the engine when the orders are filed |
| 3 | `/execute/approve` and `/reject` had no notion of which proposal they answered | defect | an optional `step`; a decision naming another step is refused with a reason that says what the engine is waiting on; the command page always sends the step it showed |
| 4 | Plan feasibility used point estimates: "0 of 24, keeps the constraint" while the 95% upper bound was 13.8% | defect | every verdict carries `evidenceSufficient` (the bound within tolerance) and a warning line; the text reads "keeps the constraint (unconfirmed by the 95% bound)" until then |
| 5 | The retreat round never fired for a lone battle: an air strike with `retreatAfterRound 1` fought four rounds | defect | the engine fights a single pending battle through the tracker, bypassing the delegate's "current battle"; both players now find the battle by the id the engine passes |
| 6 | Every strike lost all nine German aircraft; hosted Italian aircraft were removed "could not land in France" | defect | the engine removes aircraft that cannot land at the end of the non-combat move, and a territory captured this turn is not a landing place; `LandingPolicy` flies them to the nearest own or allied territory the validator accepts, the planner runs the German and Italian non-combat moves, and the staff player proposes landing orders at every non-combat move |
| 7 | `odds` expected losses included the airfield and factory although the footnote said infrastructure was excluded; `--seed` did not seed the calculator's dice; "attacker won" and "France emptied" differed (draws) | surprising | losses count combat units only; the usage text now says the seed fixes the follow-up sampling only; the draw case is documented |
| 8 | The planner accepted the same power as attacker and ally (forces counted twice), reported empty plans as feasible for targets with no force in range, and hard-coded Italy, Germany and France in its wording | defect | same power is refused; families with nothing to move, a target already held, or no war are listed under "Not evaluated" with the reason; the report names the powers it was given |
| 9 | `reach` and `legal` built one shortest route, which for four armour moves ran through neutral Yugoslavia (refused: cannot blitz) although an equally short route over German ground was legal | defect | `Routes` tries the friendliest route first, then the map's free choice; the validator still decides |
| 10 | `validate` accepted a directive with a blank acting power; the name matcher suggested 'Iraq' for 'Iran' | defect | a blank acting power is a question back to the commander; the matcher now needs four shared leading letters, containment of a four-letter name, or one edit in five on names of six letters or more |
| 11 | Illegal verdicts split the engine's text between `error` and `warnings` (too little movement is a warning) | surprising | a `reason` field carries the refusal wherever the engine put it |
| 12 | The planner numbers in the status doc (six trajectories) did not reproduce at 24 | info | the status doc carries a caveat and points here |
| 13 | The reinforce planner JSON did not say what the intervening power moved | info | `interveningOrders` in the report (the policy's orders on the starting position) |

Also noted, not changed: Neutral_Allies territories (Yugoslavia, Greece, Crete) count as enemy-held
for the Axis and are offered as attack targets from the start, which is the engine's view of strict
neutrals; `delegated_theaters` in a directive is not checked against the map (by design, a free-text
field); the `model` field of a validated directive holds the source label given on the command line.

## Round 2: the fixed build against the live file

Seven scenario agents and seven skeptics (14 Haiku agents, 77 minutes, 2.8 M tokens) re-ran the
fixed CLI on the 2nd Edition file: routes, directives, odds, other targets, the API, hosted
execution and the planner (passive and reinforcing, tolerance 0 and 15%). The scenarios passed 46 of
their 48 written checks; the two "failures" were the scenario author's own expectations, not the
tool's (see the notes under the second table). Every Round 1 fix held on the live map:

| Round 1 fix | What the live file showed (skeptic-confirmed) |
|---|---|
| 1 private copy for hosting | after a hosted German turn (two infantry into France, battle fought), `/observe` on the file was byte-identical to the observation taken before, and a second `/execute/start` began at round 1, `gameInitDelegate`, with 0 records |
| 2 orders before the engine starts | `/execute/start` with `run: false` returned `running: false`; `/execute/run` started it; the first pending proposal was `germansCombatMove` with the filed two-infantry order |
| 3 approvals name their step | approving `germansNonCombatMove` while `germansCombatMove` waited was refused with "the engine is waiting on germansCombatMove, not germansNonCombatMove"; approving before any run returned "nothing is waiting for a decision" |
| 4 evidence flag | at tolerance 0 every zero-conquest plan read "keeps the constraint (unconfirmed by the 95% bound)" with a "? 0 of 24 … as high as 13.8%" line; at tolerance 15% the same plans read plain "keeps the constraint"; `evidenceSufficient` and `warnings` present on all 11 JSON plans |
| 5 retreat round on a lone battle | three hosted runs with `retreatAfterRound 2`: the German battle in France lasted exactly 2 rounds in each; planner `meanGermanBattleRounds` 1.0 for every round-1 family, 1.96 for air-strike-r2 |
| 6 aircraft fly home | hosted runs: 1, 4 and 1 surviving aircraft of 9 were landed in Denmark by orders the staff proposed itself, all "performed"; the Italian bomber and two fighters that took France were landed in Albania; no record said "could not land" or "removed"; planner air-strike-r1 lost 5.0 of 9 aircraft on average instead of 9 |
| 7 odds losses | 12 expected-loss lists, 78 entries, none of type factory, airfield or harbour; airfield and major factory still listed among the defending units; usage text about the seed quoted |
| 8 planner guards | same power as attacker and ally refused (exit 1, "both were Japanese"); Germany as target: no ranked plan, eleven families "Germans already hold Germany"; Egypt: twelve not-evaluated entries and no empty plan; Yunnan: seven Japanese plans ranked, orders accepted 24 of 24 in every plan, title "Japanese strike, Italians take Yunnan" |
| 9 friendliest route first | GSG → Romania and Romania → GSG armour legal via Slovakia Hungary; `reach` lists Romania for the GSG armour group with that route; Italian armour Northern Italy → Romania still refused ("Cannot blitz on that route", the friendly way is 3 moves, armour has 2), which the skeptic confirmed against `reach` |
| 10 directives | blank acting power → "The directive does not say which power acts. Which power is it for?" (exit 3); "Italy" → "Did you mean 'Italians'?"; "Iran" → no suggestion (Iraq and Persia exist on the map, so the absence is a real test); "Normandy" → "Normandy Bordeaux" |
| 11 `reason` field | an illegal verdict's `reason` equalled `warnings[0]` ("Not all units have enough movement") where the engine warns and `error` where it errors |
| 13 intervening orders | the reinforce run listed six British orders (fighters, a bomber, a tactical bomber) with routes ending in France, in the JSON and the text |

Numbers from the planner on the live file, tolerance 0, passive British, 24 trajectories per plan,
before the Round 2 fixes below (wall 110 s): strafe-3land-r2 Italians own France 95.8% [79.8–99.3],
air-strike-r3 75.0% [55.1–88.0], strafe-6land-r1 58.3% [38.8–75.5], strafe-3land-r1 54.2%
[35.1–72.1], air-strike-r1 16.7% [6.7–35.9], italy-alone 0.0% [0.0–13.8]; rejected: strafe-9land-r1
(1 of 24 conquests), strafe-6land-r2 (2), strafe-9land-r2 (16), full-attack (24).

### What Round 2 found, and what changed the same evening

| # | Finding | Severity | Fix |
|---|---|---|---|
| 14 | Planner wall time far above CPU time (110 s wall for 17.8 s of CPU; one worker: 198 s wall for 20.6 s). The scenario agent found no sleep; its skeptic did: the engine's AI base class sleeps the client's "AI combat step pause" (1 s default) every time a battle asks it to confirm casualties, and the simulation players inherit it | defect | `SimulationPlayer` answers the confirmation without pausing, and the module's in-memory settings set both AI pauses to zero (the hosted path already did). Measured: the same 11 × 24 run now takes 3.4 s wall (15.1 s CPU) |
| 15 | Identical runs did not reproduce: same file, same seed, different TUV means and different splits of land units between Western Germany and Holland Belgium. Land units of equal cost and type were tie-broken by their unit id, which the engine draws at random on every load | defect | `ForceSubsets` breaks ties by origin territory before unit id; the planner passes the origins. Residual drift remains and is now stated in the report's assumptions: see "What still moves between runs" |
| 16 | Plan descriptions and the assumptions block were Paris wording on every target ("German aircraft … Italy attacks", "into France") | defect | descriptions are worded by the planner for the powers and force it finds; the assumptions are templates filled with the attacker, ally, defender and target |
| 17 | "6 cheapest land units" and "9 cheapest" ordered the same four units at Yunnan, so ranks 1–2 and 4–5 were the same plan twice | defect | a family that would order the same force as an earlier one is listed under Not evaluated ("Only 4 land units of the Japanese stand next to Yunnan, so this family orders the same force as strafe-6land-r1"); the first says "All 4 land units of the Japanese in range" |
| 18 | "Capitals on this map: United Kingdom (British)" listed only the capitals of the powers the directive named, while the map has ten | defect | the hint says what it lists: "Capitals of the powers the directive names: …" |
| 19 | "No Japanese units stand within range" while a Japanese fighter could legally fly to Yunnan: the planner's range is a round trip | surprising | the reason now reads "(land units next to it, aircraft that can fly there and back)" |
| 20 | The delayed-conquest line said "Strike on the German turn … French purchases" for Japan/Yunnan, and did not say "already hold" for a held target | surprising | worded for the powers given; a held target gets the same reason as the other families |
| 21 | A run with no evaluable family printed the ranking header over an empty table | info | "No plan family could be sampled on this position; see Not evaluated below." |
| 22 | `evidenceSufficient` was false on every rejected plan, with no warning, so its meaning for a rejection was unclear | info | a rejection for conquests is confirmed when the interval's lower bound is above the tolerance, otherwise the plan reads "REJECTED (unconfirmed by the 95% bound)" with a "could still be as low as" line; a rejection for refused orders is always confirmed. At tolerance 0 every conquest rejection is confirmed, and a kept plan's warning says that a zero tolerance cannot be confirmed by any finite sample |
| 23 | The ranking prints a strict order while neighbouring feasible plans overlap (air-strike-r2 [79.8–99.3] against air-strike-r3 [55.1–88.0]) | surprising | after the table, one line per run of feasible plans whose intervals overlap the run's first: "Rank 1 is not separated from ranks 2 to 6 on 'Italians own France': their 95% intervals overlap, so their order rests on the attacker's losses until more trajectories separate them." (Round 3 and a code review refined this: an earlier wording chained neighbours and over-claimed) |
| 24 | `reach` listed the Italian AA guns with `destinations: []` and no reason while `legal` explained the same move | surprising | a group with no destination carries the engine's first refusal ("Cannot move AA guns in combat movement phase") |
| 25 | `legal` printed "LEGAL" before its JSON, so its output was not JSON; `reach` printed bare JSON | surprising | `legal --json` prints the verdict alone; the usage text says so |
| 26 | Air-only strike: "attacker destroys all defenders" 208 of 1,000 but "France emptied" 225 of 1,000 with 0 draws | surprising | explained from the engine: when the last aircraft and the last defenders fall in the same round, the attacker list is empty first, so the engine scores the defender as holding, and the draw test fails because the factory and airfield remain among the defenders. The odds report says so under its table |
| 27 | The battle log labels the first round "round 2" | surprising | the engine's own history text adds one to its round counter; game-core is untouched and the retreat rule counts rounds correctly (2 rounds for `retreatAfterRound 2`) |
| 28 | No `germansAirborneCombatMove` proposal was ever asked for | info | the engine never prompted the step (no airborne units); not traced further |
| 29 | Hosted runs lost 8, 5 and 8 of 9 aircraft after two rounds against the full garrison | info | combat casualties, not landing removals; the planner's air-strike-r2 mean of 7.1 aircraft lost agrees. The scenario's "far below nine" expectation was written for a one-round retreat |
| 30 | Italy won the battle in France in one hosted run without taking it | info | all five Italian land units died; aircraft cannot capture. Correct engine behaviour, and the aircraft were still landed |

Also: the `legal` verdict's `reason` comes from `error` or `warnings[0]` depending on where the
engine put the text (that is the field's purpose); `reach` writes `movement` as a string because the
engine's movement is a decimal; `validate` echoes the `--source` label in `model` (by design).

### What still moves between runs

After the fixes, two runs of `plan paris --trajectories 24 --seed 7` on the live file gave the same
rank order, the same orders for every plan and the same conquest and ownership counts for 10 of 11
plans; the eleventh (strafe-9land-r2) differed by one trajectory (18 against 19 conquests of 24), and
the attacker's mean TUV lost differed by at most 0.17 on eight plans. The cause is the engine's:
unit ids are drawn at random on every load, `Unit` hashes by id, and the battle code iterates hash
sets of units, so with identical dice the casualties can fall on different units of the same kind.
Fixing it from outside the engine would mean caching the parsed game bytes between runs; the report
now states the limit instead. Within one process, two trajectories with the same seed are identical
(`SandboxTest`).

### Skeptic verdicts on the Round 2 reports

Directives and odds: trustworthy. Routes, other targets, API, execution and planner: "partly", in
each case because the scenario agent's prose over-reached its files, not because a check was wrong:
the routes agent called the Yugoslavia detour "an equally short legal alternative" without running
the two-step route (Round 1 had run it and the engine had refused it); the planner agent claimed no
sleep sat on the battle path (the skeptic found it, finding 14 above); the execution agent cited a
line that read "round 3" for "round 2"; the other-targets agent contrasted the war-status reason with
the range reason, which the planner never reaches once the first check fails. The skeptics also
marked as unverifiable every exit code and command line the agents did not save to a file; the Round
3 prompts ask for both.

## Round 3: the Round 2 fixes against the live file

Four scenario agents and four skeptics (8 Haiku agents, 21 minutes, 1.7 M tokens), each skeptic
starting as soon as its scenario's report landed. The agents saved every command line, exit code and
timing, as the Round 2 skeptics had asked. 30 of 31 written checks passed; the one failure was again
the scenario author's: "no two ranked plans have identical orders" is wrong, because a strafe that
retreats after round 1 and one that retreats after round 2 order the same units and differ only in
the retreat round, which lives outside `orders`.

| Round 2 fix | What the live file showed (skeptic-confirmed) |
|---|---|
| 14 AI pause | `plan paris --trajectories 24` twice: real 3.21 s and 3.83 s (user 14.6 s and 16.8 s) against 110 s before; three more 24-trajectory runs between 3.2 and 4.3 s |
| 15 stable tie-break | the two runs gave the same rank order and identical orders for all 11 plans; 17 of the JSON fields differed: TUV means by at most 0.08, and one rejected plan (strafe-9land-r2) by one trajectory (Italians own France 6 against 7 of 24, Germans conquer 18 against 17). The skeptic recomputed every printed Wilson interval from its counts (66 intervals, all within 2e-9) |
| 16 wording by name | the Yunnan text contained no "German", "Italy " or "France"; the assumptions named Japanese, Italians, Chinese and Yunnan with no unfilled template |
| 17 collapsed families | strafe-9land-r1 and -r2 under Not evaluated as "the same force as strafe-6land-r1/r2"; strafe-6land-r1 read "All 4 land units of the Japanese in range (no aircraft in range), retreating after round 1" |
| 18 capitals wording | "Capitals of the powers the directive names: United Kingdom (British)." for the British Iran directive; a directive naming no power got "Capitals on this map:" with all ten |
| 19, 20 reasons | air strikes: "aircraft that can fly there and back"; delayed-conquest: "turn of the Japanese … next turn of the Italians … placement of the Chinese" |
| 21 empty ranking | Egypt: "No plan family could be sampled on this position; see Not evaluated below." and no table header; 12 not-evaluated entries |
| 22 evidence on rejections | tolerance 5%: strafe-6land-r2 at 12.5% [4.3–31.0] read "REJECTED (unconfirmed by the 95% bound)" with "could still be as low as 4.3%", strafe-9land-r2 at 75.0% [55.1–88.0] and full-attack plain "REJECTED"; tolerance 15%: every 0-of-24 plan plain "keeps the constraint" |
| 24 reach reasons | the Italian AA-gun group: `destinations: []`, `reason: "Cannot move AA guns in combat movement phase"`; groups with destinations carry no reason |
| 25 `legal --json` | one JSON object, parsed whole; without the flag the first line is LEGAL or ILLEGAL and the rest is the same JSON; exit 3 for the refusal |
| 26 odds note | the mutual-loss sentence printed under the table; JSON: attacker wins 175, walk-ins 215, draws 0 of 1,000 |
| hosting unchanged | two hosted runs: German battle 2 rounds each, 3 and 1 surviving aircraft landed in Denmark, Italian aircraft landed in Albania, four `before-*.tsvg` saves per run, no errors, 1.5–1.7 s wall |

### What Round 3 found, and what changed afterwards

| # | Finding | Severity | Fix |
|---|---|---|---|
| 31 | On Yunnan, `franceEmptiedByGermany` read 6 of 6 for every strafe although Chinese infantry survived: the metric counted units at war with the *ally*, and the Italians are neutral to the Chinese, so no Chinese unit counted | defect | the metric counts the attacker's enemies. A new test: three Japanese infantry cannot empty four Chinese infantry in one round, so the count must be 0 |
| 32 | "No units of the Japanese stand within range of Yunnan (land units next to it, aircraft …)" for the air-strike families while four Japanese land units stood next to Yunnan | defect | an air-only family with no aircraft reads "No aircraft of the Japanese can fly to Yunnan and back, so this family has nothing to move." |
| 33 | "The Italians attack if Yunnan is still enemy-held" on every Yunnan plan although the Italians are neutral to the Chinese and have no unit in range | defect | "The Italians have no attack to make here." when the ally has no force or is not at war with the holder |
| 34 | "Ranks 1 to 4 lie within each other's 95% intervals … more trajectories would separate them" when all four were 0 of 6 and the order came from the attacker's losses | surprising | the line names the run's first rank and says the order rests on the attacker's losses (see 23) |
| 35 | "Germans conquers France" | info | "conquer" |
| 36 | In one hosted run the French end-turn step had no history entry | info | explained: Italy took France, the French capital; the engine gives a power without its capital no income, so the step wrote no event and the export lists none. Both runs reached round 2 |
| 37 | Same seed, odds text and JSON runs: air-only attacker wins 22.3% against 17.5% (n = 1,000 each) | info | the calculator's own dice are unseeded, as the usage text says; the intervals barely touch, so the gap is at the edge of what two independent runs produce |

The code review that ran beside the fleet (Sonnet, on the Round 2 diff) added: a plan rejected
because the engine refused its orders was marked "unconfirmed" (fixed: such a rejection is always
settled); the command page could answer a proposal it never showed, and a step name alone cannot
tell this round's proposal from the last round's (fixed: decisions carry the proposal's `since`
time, the gate refuses a stale one with "the proposal for … changed after you saw it", and the page
refuses to send a decision before it has shown the proposal); the "same force" reason blamed a land
shortage even for two identically defined families (fixed: "This family orders the same force and
retreat as …"); the full-attack description ignored a retreat round (fixed); the collapse test
accepted either outcome (fixed: it now pins both); the tie-break test could pass by luck one time in
35 (fixed: it also runs the reversed key); `/validate-directive` without a directive answered 500
(fixed: 400). Module tests: 104.

## Round 4: the Round 3 fixes against the live file

Two scenario agents and two skeptics (4 Haiku agents, 3 h 10 min wall, 0.76 M tokens), the skeptics
again starting as each report landed. The hosted-run agent was the long pole: its two approvals are
stamped 01:27 and 01:36 UTC, close to three hours after the fleet started, and it left one decision
open for 8 min 37.6 s between an approval and its next poll (the skeptic measured the gap from the
`since` and decision times). All 17 written checks passed, and both skeptics confirmed every one
from the saved files; what they refuted were two notes by the agents themselves (below).

| Round 3 fix | What the live file showed (skeptic-confirmed) |
|---|---|
| 31 emptied metric | Yunnan, strafe-3land-r1 (three Japanese infantry against four Chinese): `franceEmptiedByGermany` 0 of 6, mean defender TUV lost 2.0 |
| 32 air-only reason | air-strike-r1, -r2, -r3: "No aircraft of the Japanese can fly to Yunnan and back, so this family has nothing to move."; "No units of the Japanese" appears nowhere |
| 33 ally wording | all five Yunnan descriptions end "The Italians have no attack to make here."; the observe export shows the Chinese–Italian relationship as Neutrality and no Italian owner or unit among Yunnan's eight neighbours |
| 34 unseparated ranks | "Rank 1 is not separated from ranks 2 to 4 on 'Italians own Yunnan': their 95% intervals overlap, so their order rests on the attacker's losses until more trajectories separate them."; rank 5 (full-attack) is rejected, so the run stops at 4; "lie within each other" is gone |
| 35 conquer | France, tolerance 0: four "! Germans conquer France in … of trajectories; the directive allows at most 0.0%." lines; the only "conquers" left is the tolerance header |
| header width | "Italians own France" and the first row's 95.8% both start at column 35 |
| 22, 23 evidence wording | all seven "?" lines of kept plans end "a tolerance of zero cannot be confirmed by any finite sample, so state the chance you would accept."; tolerance 5%: strafe-6land-r2 at 12.5% [4.3–31.0] "REJECTED (unconfirmed by the 95% bound)" with "could still be as low as 4.3% (95% bound) against a tolerated 5.0%", and the 0-of-24 plans "more trajectories narrow the bound" |
| review: full attack | "The whole strike force of the Germans, fighting to the end. The Italians attack if France is still enemy-held." |
| review: `since` | the index documents `since`; a decision with `since` 2000-01-01T00:00:00Z: approved false, "the proposal for germansCombatMove changed after you saw it; the one waiting now dates from 2026-10-31T01:27:08.419802Z"; `since` "not-a-time": 400 '"since" must be an ISO-8601 instant'; the exact `since`: approved true; after the run ended: "nothing is waiting for a decision" |
| review: page label | `/ui/text.js` carries "Refresh the status first: this page has not shown the proposal it would answer." |
| review: `/validate-directive` | without `parsed`: 400 "the request body needs a \"parsed\" field" |
| hosting unchanged | `run: false` gives running false, `/execute/run` running true; the approved order (2 infantry, Holland Belgium → France) is recorded "performed" and the battle was lost, as two infantry against the French garrison should lose; the run reached round 2; the serve log holds its two startup lines and nothing else |

### What Round 4 found

| # | Finding | Severity | Outcome |
|---|---|---|---|
| 38 | Same seed, two runs (tolerance 0 and 5%): strafe-9land-r2 flipped one trajectory (Italians own France 6 against 5 of 24, Germans conquer 18 against 19), while the tolerance-0 text and JSON runs agreed on every rate. The scenario agent called the runs identical; its skeptic refuted that, and noted that the assumptions sentence promised only small differences "in losses" | info | the drift of 15 again; the assumptions sentence now says a repeated run can also flip the outcome of one trajectory. The cure (caching the parsed game bytes between runs) stays an open item in 03 |
| 39 | Tolerance 5%: the "!" line under strafe-6land-r2 states the violation flatly while the verdict reads "unconfirmed"; a reader scanning "!" lines takes the rejection as settled | surprising | by design: the verdict follows the observed rate and the bound labels the confidence; the "!" line carries the interval and the "?" line under it says how low the rate could be. Read a row as verdict, then "!", then "?". No change |
| 40 | The Yunnan JSON keeps the Paris metric names (`italyOwnsFrance`, `franceEmptiedByGermany`, `meanFrenchTuvLost`) | info | known; listed in 03's open items. No change this round |
| 41 | The hosted run's history reads "met a national objective for an additional 5 Optional[PUs]; end with 65 Optional[PUs]" | defect (engine) | traced to the engine: `TriggerAttachment` in `game-core` concatenates its `getResource()`, an `Optional<String>`, into the national-objective message (the `EndTurnDelegate` variant pluralizes " PU" properly). This module only exports the history text. No edit under `game-core` by this fork's rule; recorded in 03 as a candidate upstream bug report, to be filed only when the owner says so |
| 42 | With `others: idle`, the Game Loaded event says the fourteen other powers are "being played by: General Staff simulation" | surprising | by design: the idle stand-ins are the module's simulation player (moves nothing, buys nothing, battles still resolve); `others: fast` swaps in the engine's Fast AI, which the engine labels itself. No change |
| 43 | A decision was raised for germansNonCombatMove although `steps` filed orders only for germansCombatMove; the agent approved it with no orders after leaving it open 8 min 37.6 s, and nothing decided it in the meantime | info | by design: `steps` files orders, it does not pick stopping points; the gate asks at every movement step of a staff power so the person can add orders (at a non-combat move the staff also proposes landings), and it waits. The index line for `/execute/start` now says so |
| 44 | `/execute/stop` returned the same report as `/execute/report`, and the server kept answering | info | by design: stop ends the hosted run (already over here) and returns its final report; the serve process is separate. The index line for `/execute/stop` now says so |

### Skeptic verdicts on the Round 4 reports

Both "partly", with every check confirmed and the refutations aimed at the agents' own notes: the
planner agent's "the rates match" (38), and the hosted-run agent's claim that the no-argument usage
prints to stdout (its own capture files show the text on stderr with exit 2, as designed).
Unverifiable from the files: the hosted-run agent's reason for its pause, and who rebuilt the frozen
CLI at 17:35:47, two minutes before the first logged command (the session did, before launching the
fleet; agents never run Gradle). Module tests: 104; the two changes of this round are wording.
