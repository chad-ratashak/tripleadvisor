// WIRING ONLY: talks to the local API and fills the page. Wording is in text.js, layout in index.html.
(function () {
  const T = window.STAFF_TEXT;
  const $ = (id) => document.getElementById(id);
  let lastPlanReport = null;

  function fillText() {
    document.querySelectorAll("[data-text]").forEach((el) => {
      const path = el.getAttribute("data-text").split(".");
      let value = T;
      for (const key of path) value = value && value[key];
      if (value !== undefined) {
        if (el.tagName === "INPUT" && el.type !== "button") el.placeholder = value;
        else el.textContent = value;
      }
    });
    document.title = T.title;
  }

  async function post(path, body) {
    const response = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const text = await response.text();
    let data;
    try { data = JSON.parse(text); } catch (e) { data = { error: text }; }
    if (!response.ok) throw new Error(data.error || response.status);
    return data;
  }

  function show(id, value) {
    $(id).textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
  }

  function busy(id) { show(id, T.labels.working); }

  function game() { return $("game").value.trim(); }

  async function run(outId, action) {
    busy(outId);
    try { show(outId, await action()); } catch (e) { show(outId, T.labels.failed + e.message); }
  }

  function renderPlans(report) {
    lastPlanReport = report;
    const rows = report.plans.map((p) => `<tr><td>${p.rank}</td><td>${p.id}</td><td>${fmt(p.metrics.italyOwnsFrance)}</td><td>${fmt(p.metrics.germanyConquersFrance)}</td><td>${Math.round(p.metrics.allOrdersAccepted.rate * 100)}%</td><td>${p.metrics.meanGermanTuvLost.toFixed(1)}</td><td>${p.feasible ? T.verdicts.feasible : T.verdicts.rejected}</td></tr>`).join("");
    $("plans").innerHTML = `<table><thead><tr><th>${T.columns.rank}</th><th>${T.columns.plan}</th><th>${T.columns.italy}</th><th>${T.columns.germany}</th><th>${T.columns.orders}</th><th>${T.columns.tuv}</th><th>${T.columns.verdict}</th></tr></thead><tbody>${rows}</tbody></table>`;
    return report;
  }

  function fmt(p) { return `${(p.rate * 100).toFixed(1)}% [${(p.ciLow * 100).toFixed(1)}-${(p.ciHigh * 100).toFixed(1)}] n=${p.n}`; }

  function ordersFromTopPlan() {
    const plan = lastPlanReport && lastPlanReport.plans.find((p) => p.feasible);
    if (!plan) throw new Error(T.labels.nothingPending);
    return plan.orders.filter((s) => s.step.startsWith("germans")).map((s) => ({
      step: s.step,
      retreatAfterRound: plan.retreatAfterRound,
      orders: s.orders.flatMap((o) => o.units.map((u) => ({ route: o.route, unitType: u.type, count: u.count })))
    }));
  }

  function renderStatus(status) {
    const pending = status.pending;
    $("pending").textContent = pending ? T.labels.pending + "\n" + JSON.stringify(pending, null, 2) : T.labels.nothingPending;
    return status;
  }

  function wire() {
    $("observe").onclick = () => run("observeOut", () => post("/observe", { game: game(), focus: ["France"] }));
    $("advise").onclick = () => run("adviseOut", () => post("/parse-directive", { game: game(), directive: $("directive").value, model: $("model").value || null }));
    $("plan").onclick = () => run("planOut", async () => renderPlans(await post("/plan/paris", { game: game(), trajectories: Number($("trajectories").value), tolerance: Number($("tolerance").value) / 100, intervening: $("intervening").value })));
    $("explain").onclick = () => run("planOut", async () => { const r = await post("/plan/paris", { game: game(), trajectories: Number($("trajectories").value), tolerance: Number($("tolerance").value) / 100, intervening: $("intervening").value, explain: true, model: $("model").value || null }); renderPlans(r.report); return r.briefing; });
    $("start").onclick = () => run("executeOut", async () => renderStatus(await post("/execute/start", { game: game(), staff: ["Germans"], others: $("others").value, mode: $("mode").value })));
    $("sendOrders").onclick = () => run("executeOut", async () => renderStatus(await post("/execute/orders", { steps: ordersFromTopPlan() })));
    $("approve").onclick = () => run("executeOut", async () => { await post("/execute/approve", { by: "web" }); return renderStatus(await post("/execute/status", {})); });
    $("reject").onclick = () => run("executeOut", async () => { await post("/execute/reject", { by: "web" }); return renderStatus(await post("/execute/status", {})); });
    $("status").onclick = () => run("executeOut", async () => renderStatus(await post("/execute/status", {})));
    $("report").onclick = () => run("executeOut", () => post("/execute/report", {}));
    $("stop").onclick = () => run("executeOut", () => post("/execute/stop", {}));
    $("doctrineList").onclick = () => run("doctrineOut", () => post("/doctrine/list", { file: $("doctrineFile").value }));
    $("doctrineAdd").onclick = () => run("doctrineOut", () => post("/doctrine/add", { file: $("doctrineFile").value, title: $("doctrineTitle").value, directive: $("directive").value, game: game(), model: $("model").value || null }));
    $("doctrineReconcile").onclick = () => run("doctrineOut", () => post("/doctrine/reconcile", { file: $("doctrineFile").value, game: game() }));
    setInterval(() => { if ($("autoStatus").checked) post("/execute/status", {}).then(renderStatus).catch(() => {}); }, 3000);
  }

  fillText();
  wire();
})();
