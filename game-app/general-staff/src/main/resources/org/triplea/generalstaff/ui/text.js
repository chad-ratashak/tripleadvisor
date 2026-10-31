// WORDING ONLY. Every word the command page shows lives here, in reading order.
// Layout lives in index.html; wiring lives in app.js. Change wording here and nowhere else.
window.STAFF_TEXT = {
  title: "TripleA General Staff",
  subtitle: "Read the board, ask the engine, approve before anything moves.",
  gameLabel: "Game file (absolute path to a map XML or .tsvg save)",
  gamePlaceholder: "/path/to/ww2global40_2nd_edition.xml",
  modelLabel: "Language model",
  sections: {
    observe: "Position",
    advise: "Directive",
    plan: "Operation Paris plans",
    execute: "Execution (approval gate)",
    doctrine: "Doctrine"
  },
  buttons: {
    observe: "Observe France",
    advise: "Parse directive",
    plan: "Evaluate plans",
    explain: "Brief me",
    start: "Host game (staff plays Germans; nothing moves until Run)",
    sendOrders: "Send top plan's orders",
    run: "Run to the end of round 1",
    approve: "Approve",
    reject: "Reject",
    status: "Refresh status",
    report: "After-action report",
    stop: "Stop hosted game",
    doctrineList: "List doctrine",
    doctrineAdd: "Add standing directive",
    doctrineReconcile: "Reconcile with the board"
  },
  labels: {
    directive: "Commander's directive",
    trajectories: "Trajectories per plan",
    tolerance: "Tolerated chance of a German conquest (%)",
    intervening: "Allied move between German and Italian turns",
    others: "Other powers",
    mode: "Gate",
    doctrineFile: "Doctrine file (absolute path, created if missing)",
    doctrineTitle: "Title",
    pending: "The engine is waiting for your decision",
    nothingPending: "Nothing is waiting for approval.",
    notShown: "Refresh the status first: this page has not shown the proposal it would answer.",
    status: "Status",
    working: "Working…",
    failed: "The request failed: "
  },
  options: {
    passive: "none",
    reinforce: "reinforce France",
    idle: "idle stand-ins",
    fast: "Fast AI",
    manual: "manual approval",
    autoApprove: "approve everything",
    autoReject: "reject everything"
  },
  columns: {
    rank: "rank",
    plan: "plan",
    italy: "Italy owns France",
    germany: "Germany conquers France",
    orders: "orders accepted",
    tuv: "German TUV lost",
    verdict: "verdict"
  },
  verdicts: { feasible: "keeps the constraint", feasibleThin: "keeps the constraint (unconfirmed by the 95% bound)", rejected: "rejected", rejectedThin: "rejected (unconfirmed by the 95% bound)" },
  footer: "Every rate shows its sample size and a 95% interval. The language model never decides legality or odds."
};
