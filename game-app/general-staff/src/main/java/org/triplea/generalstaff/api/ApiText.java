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
      POST /plan/paris             {"game","trajectories"?,"seed"?,"tolerance"?,"intervening"?,"explain"?,"intent"?,"model"?}
      POST /parse-directive        {"game","directive","model"?}
      POST /explain                {"game","intent"?,"runs"?,"seed"?,"model"?}  -> {"report","briefing"}
      POST /explain-territory      {"game","territory","intent"?,"model"?}
      POST /execute/start          {"game","staff":[<player>...],"others"?: "idle"|"fast","mode"?: "manual"|"auto-approve"|"auto-reject","lastRound"?,"timeoutMinutes"?,"work"?}
      POST /execute/orders         {"steps":[{"step","retreatAfterRound"?,"orders":[{"route":[...],"unitType","count"}]}]}
      POST /execute/status         {}
      POST /execute/approve        {"by"?,"orders"?: [...]}   (edited orders replace the proposal)
      POST /execute/reject         {"by"?}
      POST /execute/report         {}
      POST /execute/stop           {}
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
}
