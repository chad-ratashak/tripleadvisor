package org.triplea.generalstaff.api;

/**
 * WORDING ONLY. Everything the local HTTP API says in prose lives here. Routing lives in {@link
 * LocalHttpApi}; change wording here and nowhere else.
 */
final class ApiText {
  private ApiText() {}

  static final String INDEX =
      """
      TripleA General Staff local API. Read-only: nothing here changes a game.

      GET  /health                 -> {"status","ollama","model","models"}
      POST /observe                {"game": <file>, "focus": [<territory>...]}
      POST /history                {"game": <file>}
      POST /legal                  {"game","step","from","to","type","count"}
      POST /reach                  {"game","step","from"?}
      POST /odds/paris             {"game","runs"?,"seed"?,"maxPools"?}
      POST /odds/strafe            {"game","attacker","defender","territory","rounds"?,"landStep"?,"runs"?}
      POST /plan/paris             {"game","trajectories"?,"seed"?,"tolerance"?,"intervening"?,"explain"?,"intent"?,"model"?,"attacker"?,"ally"?,"defender"?,"target"?}
      POST /validate-directive     {"game","parsed": {<directive JSON>},"source"?}
      POST /parse-directive        {"game","directive","model"?}
      POST /explain                {"game","intent"?,"runs"?,"seed"?,"model"?}  -> {"report","briefing"}
      POST /explain-territory      {"game","territory","intent"?,"model"?}
      POST /execute/start          {"game","staff":[<player>...],"others"?: "idle"|"fast","mode"?: "manual"|"auto-approve"|"auto-reject","steps"?: [...],"run"?: true|false,"lastRound"?,"timeoutMinutes"?,"work"?}
                                   (hosts a private copy of the game; "steps" files orders before the engine starts, and the gate then asks at every movement step of a staff power, filed orders or not; "run": false waits for /execute/run)
      POST /execute/run            {"lastRound"?}
      POST /execute/orders         {"steps":[{"step","retreatAfterRound"?,"orders":[{"route":[...],"unitType","count"}]}]}
      POST /execute/status         {}
      POST /execute/approve        {"by"?,"step"?,"since"?,"orders"?: [...]}   (edited orders replace the proposal; a "step" that is not the pending one, or a "since" older than the pending proposal's, is refused)
      POST /execute/reject         {"by"?,"step"?,"since"?}
      POST /execute/report         {}
      POST /execute/stop           {}   (ends the hosted run and returns its final report, or {"stopped": false} when none is running; the server keeps serving)
      POST /doctrine/list          {"file"}
      POST /doctrine/add           {"file","title"?,"directive","game"?,"model"?,"parsed"?,"standing"?,"notes"?}
      POST /doctrine/remove        {"file","id"}
      POST /doctrine/reconcile     {"file","game"}
      GET  /ui                     the command page (static; talks to the endpoints above)

      <file> is an absolute path to a map game XML or a .tsvg save game on this machine.
      The server listens on the loopback interface only.
      """;

  static final String SERVING = "General Staff API listening on http://127.0.0.1:%d/ (model %s)";
  static final String NOT_FOUND = "no such endpoint: %s";
  static final String METHOD_NOT_ALLOWED = "use GET for / and /health, POST for everything else";
  static final String MISSING_FIELD = "the request body needs a \"%s\" field";
  static final String BAD_JSON = "the request body is not a JSON object";
  static final String NOTHING_PENDING = "nothing is waiting for a decision";
  static final String STEP_MISMATCH = "the engine is waiting on %s, not %s";
  static final String PROPOSAL_CHANGED =
      "the proposal for %s changed after you saw it; the one waiting now dates from %s";
  static final String BAD_SINCE = "\"since\" must be an ISO-8601 instant, got '%s'";
  static final String ALREADY_RUNNING = "the hosted game is already running";
}
