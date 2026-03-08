/* ============================================================
   SchedulrX — script.js
   ============================================================ */

let currentProcessCount = 0;
const MAX_PROCESSES = 15;
const pidColorMap = {};
let colorCounter = 0;
let lastSimulationData = null;

const ALGO_INFO = {
    FCFS:        "Processes execute in order of arrival. Non-preemptive. Simple but may cause convoy effect.",
    SJF:         "Shortest burst time runs next. Non-preemptive. Optimal avg wait time but may starve long jobs.",
    SRTF:        "Preemptive SJF. A new arrival with shorter remaining time preempts the current process.",
    RR:          "Each process gets a fixed time slice (quantum). Preemptive. Fair but higher context-switch overhead.",
    PRIORITY_NP: "Highest priority process runs to completion. Non-preemptive. Select priority mode below.",
    PRIORITY_P:  "Higher priority arrival preempts current process. Preemptive. Select priority mode below."
};

// Colour palettes — index matches proc-color-N in CSS
const LIGHT_PAL = [
    {bg:'#bfdbfe',fg:'#1e3a8a'},{bg:'#bbf7d0',fg:'#14532d'},{bg:'#fde68a',fg:'#78350f'},
    {bg:'#fecdd3',fg:'#881337'},{bg:'#ddd6fe',fg:'#4c1d95'},{bg:'#fed7aa',fg:'#7c2d12'},
    {bg:'#a5f3fc',fg:'#164e63'},{bg:'#c7d2fe',fg:'#312e81'},{bg:'#fbcfe8',fg:'#831843'},
    {bg:'#a7f3d0',fg:'#065f46'},{bg:'#fef08a',fg:'#713f12'},{bg:'#99f6e4',fg:'#134e4a'},
    {bg:'#e9d5ff',fg:'#581c87'},{bg:'#bae6fd',fg:'#0c4a6e'},{bg:'#fca5a5',fg:'#7f1d1d'}
];
const DARK_PAL = [
    {bg:'#0c4a6e',fg:'#7dd3fc'},{bg:'#134e4a',fg:'#5eead4'},{bg:'#164e63',fg:'#22d3ee'},
    {bg:'#0e7490',fg:'#cffafe'},{bg:'#155e75',fg:'#a5f3fc'},{bg:'#065f46',fg:'#6ee7b7'},
    {bg:'#0f766e',fg:'#99f6e4'},{bg:'#1e40af',fg:'#bfdbfe'},{bg:'#1d4ed8',fg:'#dbeafe'},
    {bg:'#0369a1',fg:'#e0f2fe'},{bg:'#0c4a6e',fg:'#38bdf8'},{bg:'#0f766e',fg:'#2dd4bf'},
    {bg:'#155e75',fg:'#67e8f9'},{bg:'#0e7490',fg:'#bae6fd'},{bg:'#065f46',fg:'#a7f3d0'}
];

function isDark() {
    return document.documentElement.classList.contains('dark');
}

/* ── Theme toggle ───────────────────────────────────────────── */
function toggleTheme() {
    const html      = document.documentElement;
    const dark      = html.classList.toggle('dark');
    html.classList.toggle('light', !dark);

    // Update theme pill
    const pill      = document.getElementById("theme-pill");
    const textLight = document.getElementById("theme-text-light");
    const textDark  = document.getElementById("theme-text-dark");
    const themeBg   = document.getElementById("theme-toggle");

    if (dark) {
        // Dark mode — pill slides right, moon highlighted
        if (pill)      { pill.style.transform = "translateX(100%)"; pill.style.backgroundColor = "#22d3ee"; }
        if (textLight) { textLight.style.opacity = "0.35"; }
        if (textDark)  { textDark.style.opacity  = "1"; }
        if (themeBg)   { themeBg.style.backgroundColor = "#0f172a"; }
    } else {
        // Light mode — pill on left, sun highlighted
        if (pill)      { pill.style.transform = "translateX(0%)"; pill.style.backgroundColor = "#0ea5e9"; }
        if (textLight) { textLight.style.opacity = "1"; }
        if (textDark)  { textDark.style.opacity  = "0.35"; }
        if (themeBg)   { themeBg.style.backgroundColor = "#ffffff"; }
    }

    // Re-render gantt blocks so inline colors update.
    if (lastSimulationData) {
        renderGanttChart(lastSimulationData.ganttBlocks);
    }
}

/* ── Priority Mode Toggle ───────────────────────────────────── */
let prioHighIsHigh = false;

function getPillColor() {
    return isDark() ? "#22d3ee" : "#0ea5e9";
}
function getPillTextActive() { return isDark() ? "#0f172a" : "#ffffff"; }
function getPillTextInactive() { return isDark() ? "#22d3ee" : "#0ea5e9"; }

function togglePriorityMode() {
    prioHighIsHigh = !prioHighIsHigh;

    const pill     = document.getElementById("prio-pill");
    const textLow  = document.getElementById("prio-text-low");
    const textHigh = document.getElementById("prio-text-high");
    const note     = document.getElementById("prio-note");
    const toggle   = document.getElementById("prio-toggle");
    toggle.dataset.state = prioHighIsHigh ? "high" : "low";

    pill.style.backgroundColor = getPillColor();

    if (prioHighIsHigh) {
        pill.style.transform     = "translateX(100%)";
        textLow.style.color      = getPillTextInactive();
        textHigh.style.color     = getPillTextActive();
        note.innerHTML = "⚡ Higher number = Higher priority (e.g. 3 &gt; 2 &gt; 1)";
    } else {
        pill.style.transform     = "translateX(0%)";
        textLow.style.color      = getPillTextActive();
        textHigh.style.color     = getPillTextInactive();
        note.innerHTML = "💡 Lower number = Higher priority (e.g. 1 &gt; 2 &gt; 3) — OS textbook default";
    }
}

function resetPriorityToggle() {
    prioHighIsHigh = false;
    const pill     = document.getElementById("prio-pill");
    const textLow  = document.getElementById("prio-text-low");
    const textHigh = document.getElementById("prio-text-high");
    const note     = document.getElementById("prio-note");
    const toggle   = document.getElementById("prio-toggle");
    if (!toggle) return;
    toggle.dataset.state = "low";
    if (pill)     { pill.style.transform = "translateX(0%)"; pill.style.backgroundColor = getPillColor(); }
    if (textLow)  { textLow.style.color  = getPillTextActive(); }
    if (textHigh) { textHigh.style.color = getPillTextInactive(); }
    if (note) note.innerHTML = "💡 Lower number = Higher priority (e.g. 1 &gt; 2 &gt; 3) — OS textbook default";
}

/* ── Algo change ────────────────────────────────────────────── */
function handleAlgoChange() {
    const algo = document.getElementById("algo-select").value;
    const isPriority = algo.startsWith("PRIORITY");
    const qc = document.getElementById("quantum-container");
    algo === "RR" ? qc.classList.remove("hidden") : qc.classList.add("hidden");

    // Show/hide priority mode toggle
    const pc = document.getElementById("priority-mode-container");
    isPriority ? pc.classList.remove("hidden") : pc.classList.add("hidden");
    // Reset to default when switching away
    if (!isPriority) resetPriorityToggle();

    const info = document.getElementById("algo-info");
    info.style.transition = "opacity .2s, transform .2s";
    info.style.opacity = "0"; info.style.transform = "translateY(4px)";
    setTimeout(() => {
        info.innerText = ALGO_INFO[algo] || "";
        info.style.opacity = "1"; info.style.transform = "translateY(0)";
    }, 180);

    const headerRow  = document.getElementById("table-header-row");
    const existingPH = document.getElementById("prio-th");
    const actionH    = document.getElementById("action-header");

    if (isPriority && !existingPH) {
        const th = document.createElement("th");
        th.id = "prio-th";
        th.className = "py-3 px-4 text-[10px] font-black text-sky-400 dark:text-cyan-800 uppercase tracking-widest font-mono";
        th.innerText = "Priority";
        headerRow.insertBefore(th, actionH);
    } else if (!isPriority && existingPH) {
        existingPH.remove();
    }

    document.querySelectorAll("#process-table-body tr").forEach(row => {
        const ec = row.querySelector(".prio-td");
        const lc = row.querySelector("td:last-child");
        if (isPriority && !ec) {
            const td = document.createElement("td");
            td.className = "py-3 px-4 prio-td";
            td.innerHTML = `<input type="number" min="1" class="priority w-20 bg-transparent text-skytext dark:text-cyan-400 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="1">`;
            row.insertBefore(td, lc);
        } else if (!isPriority && ec) {
            ec.remove();
        }
    });
}

/* ── Add row ────────────────────────────────────────────────── */
function addProcessRow() {
    if (currentProcessCount >= MAX_PROCESSES) { showToast("Maximum 15 processes!"); return; }
    currentProcessCount++;
    const tbody = document.getElementById("process-table-body");
    const row   = document.createElement("tr");
    row.className = "row-enter hover:bg-sky-50 dark:hover:bg-cyberedge/20 transition-colors duration-150";

    const algo = document.getElementById("algo-select").value;
    const isPriority = algo.startsWith("PRIORITY");

    let html = `
        <td class="py-3 px-5 font-black text-skyaccent dark:text-cybertxt font-mono text-sm">P${currentProcessCount}</td>
        <td class="py-3 px-4"><input type="number" min="0"  class="at w-20 bg-transparent text-skytext dark:text-slate-300 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="0"></td>
        <td class="py-3 px-4"><input type="number" min="1"  class="bt w-20 bg-transparent text-skytext dark:text-slate-300 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="1"></td>`;
    if (isPriority) {
        html += `<td class="py-3 px-4 prio-td"><input type="number" min="1" class="priority w-20 bg-transparent text-skytext dark:text-cyan-400 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="1"></td>`;
    }
    html += `<td class="py-3 px-5 text-right"><button onclick="removeRow(this)" class="w-7 h-7 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-400 font-black text-sm hover:bg-red-100 dark:hover:bg-red-900/40 hover:scale-110 transition-all duration-150 flex items-center justify-center ml-auto">✕</button></td>`;

    row.innerHTML = html;
    tbody.appendChild(row);
    updateCounter();
}

/* ── Remove row ─────────────────────────────────────────────── */
function removeRow(btn) {
    const row = btn.closest('tr');
    row.style.transition = "opacity .2s, transform .2s";
    row.style.opacity = "0"; row.style.transform = "translateX(-12px)";
    setTimeout(() => { row.remove(); currentProcessCount--; updateCounter(); }, 200);
}

/* ── Clear all ──────────────────────────────────────────────── */
function clearAllProcesses() {
    const rows = document.querySelectorAll("#process-table-body tr");
    rows.forEach((r, i) => setTimeout(() => {
        r.style.transition = "opacity .15s, transform .15s";
        r.style.opacity = "0"; r.style.transform = "translateX(-10px)";
    }, i * 40));
    setTimeout(() => {
        document.getElementById("process-table-body").innerHTML = "";
        currentProcessCount = 0; updateCounter();
        document.getElementById("results-area").classList.add("hidden");
        document.getElementById("results-area").classList.remove("visible");
        Object.keys(pidColorMap).forEach(k => delete pidColorMap[k]);
        colorCounter = 0; lastSimulationData = null;
    }, rows.length * 40 + 200);
}

function updateCounter() {
    document.getElementById("process-counter").innerText = `${currentProcessCount} / 15`;
}

/* ── Simulate ───────────────────────────────────────────────── */
async function startSimulation() {
    const rows = document.querySelectorAll("#process-table-body tr");
    if (!rows.length) { showToast("Add at least one process!"); return; }

    const algo    = document.getElementById("algo-select").value;
    const quantum = algo === "RR" ? (parseInt(document.getElementById("quantum-input")?.value) || 2) : null;

    const isPriority   = algo.startsWith("PRIORITY");
    const highIsHigh   = isPriority && document.getElementById("prio-toggle")?.dataset.state === "high";

    // Build raw process list
    let processes = Array.from(rows).map(row => ({
        pid:         row.querySelector("td:first-child").innerText.trim(),
        arrivalTime: parseInt(row.querySelector(".at").value)       || 0,
        burstTime:   Math.max(1, parseInt(row.querySelector(".bt").value) || 1),
        priority:    row.querySelector(".priority") ? parseInt(row.querySelector(".priority").value) || 1 : 0
    }));

    // Flip priorities if "Higher number = Higher priority" is selected
    // Backend always uses lower number = higher priority internally
    if (isPriority && highIsHigh) {
        const maxPrio = Math.max(...processes.map(p => p.priority));
        processes = processes.map(p => ({ ...p, priority: (maxPrio + 1) - p.priority }));
    }

    const payload = { algorithm: algo, quantum, processes };

    const btn = document.getElementById("run-btn");
    btn.innerHTML = "⏳ Running..."; btn.disabled = true; btn.style.opacity = ".7";

    try {
        const res  = await fetch('/api/simulate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const text = await res.text();
        let data;
        try { data = JSON.parse(text); }
        catch { showToast("Invalid server response."); return; }
        if (!res.ok) { showToast(data.message || data.error || "Simulation failed!"); return; }
        renderResults(data);
    } catch (err) {
        console.error("Simulation error:", err);
        showToast("Error: " + err.message);
    } finally {
        btn.innerHTML = "▶ Run Simulation"; btn.disabled = false; btn.style.opacity = "1";
    }
}

/* ── Render results ─────────────────────────────────────────── */
function renderResults(data) {
    lastSimulationData = data;

    const area = document.getElementById("results-area");
    area.classList.remove("hidden");
    area.classList.add("visible");

    const lbl = document.getElementById("gantt-algo-label");
    if (lbl) lbl.innerText = data.algorithmUsed + (data.quantumUsed ? ` | Q=${data.quantumUsed}` : "");

    renderGanttChart(data.ganttBlocks);
    renderMetricsTable(data);
    setTimeout(() => area.scrollIntoView({ behavior: "smooth", block: "start" }), 120);
}

/* ── Gantt chart ────────────────────────────────────────────── */
function renderGanttChart(blocks) {
    const blocksRow   = document.getElementById("gantt-blocks");
    const timelineRow = document.getElementById("gantt-timeline");
    if (!blocksRow || !timelineRow) return;
    blocksRow.innerHTML   = "";
    timelineRow.innerHTML = "";
    if (!blocks?.length) return;

    const dark = isDark();

    blocks.forEach((block, i) => {
        const dur  = block.end - block.start;
        const idle = block.pid === "IDLE";
        const div  = document.createElement("div");

        div.className = "gantt-block" + (idle ? " idle-block" : "");
        div.style.flex     = `${Math.max(dur, 0.5)} 0 auto`;
        div.style.minWidth = idle ? "36px" : "48px";
        div.style.setProperty("--wipe-delay", `${(i * 0.09).toFixed(2)}s`);

        if (!idle) {
            if (pidColorMap[block.pid] === undefined) {
                pidColorMap[block.pid] = colorCounter % 15;
                colorCounter++;
            }
            const ci  = pidColorMap[block.pid];
            const pal = dark ? DARK_PAL[ci] : LIGHT_PAL[ci];
            // Apply colours inline — reliable regardless of CSS cascade
            div.style.background = pal.bg;
            div.style.color      = pal.fg;
        }

        // Border radius
        if (blocks.length === 1)          div.style.borderRadius = "12px";
        else if (i === 0)                 div.style.borderRadius = "12px 0 0 12px";
        else if (i === blocks.length - 1) div.style.borderRadius = "0 12px 12px 0";

        div.title = idle
            ? `CPU IDLE: ${block.start} → ${block.end} (${dur} units)`
            : `${block.pid}: ${block.start} → ${block.end} (${dur} units)`;

        const label = document.createElement("span");
        label.innerText = idle ? "—" : block.pid;
        div.appendChild(label);
        blocksRow.appendChild(div);
    });

    // Build timeline ticks after DOM paint
    requestAnimationFrame(() => {
        const blockEls = blocksRow.querySelectorAll(".gantt-block");
        const totalW   = blocksRow.scrollWidth;
        timelineRow.style.width = totalW + "px";

        const tickColor  = dark ? "#334155" : "#94a3b8";
        const labelColor = dark ? "#64748b"  : "#1e293b";

        let cumX = 0;
        const seen = new Set();

        blockEls.forEach((el, i) => {
            const t = blocks[i].start;
            if (!seen.has(t)) {
                seen.add(t);
                addTick(timelineRow, t, cumX, tickColor, labelColor);
            }
            cumX += el.offsetWidth;
        });

        // Final end tick
        const lastT = blocks[blocks.length - 1].end;
        if (!seen.has(lastT)) {
            addTick(timelineRow, lastT, cumX, tickColor, labelColor);
        }
    });
}

function addTick(container, time, x, tickColor, labelColor) {
    const tick  = document.createElement("div");
    tick.className = "gantt-tick";
    tick.style.left = x + "px";

    const line = document.createElement("div");
    line.className = "gantt-tick-line";
    line.style.background = tickColor;

    const lbl = document.createElement("div");
    lbl.className = "gantt-tick-label";
    lbl.style.color = labelColor;
    lbl.innerText = time;

    tick.appendChild(line);
    tick.appendChild(lbl);
    container.appendChild(tick);
}

/* ── Metrics table ───────────────────────────────────────────
   Uses CSS classes that read from CSS variables set on <html>.
   Theme switching is instant — no JS re-render required.
   ─────────────────────────────────────────────────────────── */
function renderMetricsTable(data) {
    const dark = isDark();

    // Badge colours for PID cells (still inline since they're per-process)
    function badge(pid) {
        const ci  = pidColorMap[pid] ?? 0;
        const pal = dark ? DARK_PAL[ci] : LIGHT_PAL[ci];
        return `<span style="background:${pal.bg};color:${pal.fg};padding:3px 12px;border-radius:6px;font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:800;display:inline-block">${pid}</span>`;
    }

    const th = (t) => `<th class="mt-th" style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:800;letter-spacing:3px;text-transform:uppercase;white-space:nowrap">${t}</th>`;

    let html = `
    <div class="mt-wrap" style="border:1px solid;border-radius:16px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12)">
      <div class="mt-head" style="padding:14px 18px;border-bottom:1px solid;display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between">
        <span class="mt-th" style="font-family:'JetBrains Mono',monospace;font-size:10px;font-weight:800;letter-spacing:4px;text-transform:uppercase">Process Metrics</span>
        <div style="display:flex;flex-wrap:wrap;gap:14px;font-family:'JetBrains Mono',monospace;font-size:11px">
          <span class="mt-label">Avg TAT: <b class="mt-tat">${data.averageTAT.toFixed(2)}</b></span>
          <span class="mt-label">Avg WT: <b class="mt-wt">${data.averageWT.toFixed(2)}</b></span>
          <span class="mt-label">Avg RT: <b class="mt-rt">${data.averageRT.toFixed(2)}</b></span>
        </div>
      </div>
      <div style="overflow-x:auto">
        <table style="width:100%;border-collapse:collapse;text-align:center;min-width:500px">
          <thead>
            <tr class="mt-head" style="border-bottom:1px solid">
              ${['PID','Arrival','Burst','CT','TAT','WT','RT'].map(th).join('')}
            </tr>
          </thead>
          <tbody>`;

    data.metrics.forEach(p => {
        html += `
            <tr class="mt-row" style="border-bottom:1px solid">
              <td style="padding:10px 14px">${badge(p.pid)}</td>
              <td class="mt-muted" style="padding:10px 14px;font-family:'JetBrains Mono',monospace">${p.arrivalTime}</td>
              <td class="mt-muted" style="padding:10px 14px;font-family:'JetBrains Mono',monospace">${p.burstTime}</td>
              <td class="mt-ct"    style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${p.completionTime}</td>
              <td class="mt-tat"   style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${p.turnaroundTime}</td>
              <td class="mt-wt"    style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${p.waitingTime}</td>
              <td class="mt-rt"    style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${p.responseTime}</td>
            </tr>`;
    });

    html += `
          </tbody>
          <tfoot>
            <tr class="mt-foot" style="border-top:1px solid">
              <td colspan="4" class="mt-th" style="padding:10px 14px;text-align:right;font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:800;letter-spacing:3px;text-transform:uppercase">Average</td>
              <td class="mt-tat" style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${data.averageTAT.toFixed(2)}</td>
              <td class="mt-wt"  style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${data.averageWT.toFixed(2)}</td>
              <td class="mt-rt"  style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800">${data.averageRT.toFixed(2)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>`;

    document.getElementById("metrics-table-container").innerHTML = html;
}

/* ── Toast ──────────────────────────────────────────────────── */
function showToast(msg) {
    document.getElementById("toast")?.remove();
    const t = document.createElement("div");
    t.id = "toast";
    t.className = "fixed bottom-6 left-1/2 -translate-x-1/2 z-50";
    t.style.cssText = `font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:700;padding:10px 20px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.3);background:${isDark() ? '#00e5ff' : '#0c4a6e'};color:${isDark() ? '#030d12' : '#fff'}`;
    t.innerText = msg;
    document.body.appendChild(t);
    setTimeout(() => {
        t.style.transition = "opacity .3s, transform .3s";
        t.style.opacity = "0"; t.style.transform = "translate(-50%,8px)";
        setTimeout(() => t.remove(), 300);
    }, 2800);
}

/* ── Init ───────────────────────────────────────────────────── */
window.addEventListener("DOMContentLoaded", () => {
    // Ensure light mode on start
    document.documentElement.classList.add('light');
    document.documentElement.classList.remove('dark');

    // Init theme pill — start on light (sun side)
    const themePill = document.getElementById("theme-pill");
    const textLight = document.getElementById("theme-text-light");
    const textDark  = document.getElementById("theme-text-dark");
    if (themePill) themePill.style.transform = "translateX(0%)";
    if (textLight) textLight.classList.remove("opacity-50");
    if (textDark)  textDark.classList.add("opacity-50");

    setTimeout(() => addProcessRow(), 100);
    setTimeout(() => addProcessRow(), 200);
    setTimeout(() => addProcessRow(), 300);
    handleAlgoChange();
});
