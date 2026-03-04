/* ============================================================
   SchedulrX — script.js
   ============================================================ */

let currentProcessCount = 0;
const MAX_PROCESSES = 15;
const pidColorMap = {};
let colorCounter = 0;
let lastSimulationData = null; // Tracks data for theme toggling

const ALGO_INFO = {
    FCFS:        "Processes execute in order of arrival. Non-preemptive. Simple but may cause convoy effect.",
    SJF:         "Shortest burst time runs next. Non-preemptive. Optimal avg wait time but may starve long jobs.",
    SRTF:        "Preemptive SJF. A new arrival with shorter remaining time preempts the current process.",
    RR:          "Each process gets a fixed time slice (quantum). Preemptive. Fair but higher context-switch overhead.",
    PRIORITY_NP: "Highest priority process runs to completion. Non-preemptive. Lower number = higher priority.",
    PRIORITY_P:  "Higher priority arrival preempts current process. Lower number = higher priority."
};

// Inline color palette — applied directly to gantt blocks and PID badges
// Index matches proc-color-N in CSS (light/dark handled separately)
const DARK_COLORS = [
    { bg:'#0c4a6e', fg:'#7dd3fc' },
    { bg:'#134e4a', fg:'#5eead4' },
    { bg:'#164e63', fg:'#22d3ee' },
    { bg:'#0e7490', fg:'#cffafe' },
    { bg:'#155e75', fg:'#a5f3fc' },
    { bg:'#065f46', fg:'#6ee7b7' },
    { bg:'#0f766e', fg:'#99f6e4' },
    { bg:'#1e40af', fg:'#bfdbfe' },
    { bg:'#1d4ed8', fg:'#dbeafe' },
    { bg:'#0369a1', fg:'#e0f2fe' },
    { bg:'#0c4a6e', fg:'#38bdf8' },
    { bg:'#0f766e', fg:'#2dd4bf' },
    { bg:'#155e75', fg:'#67e8f9' },
    { bg:'#0e7490', fg:'#bae6fd' },
    { bg:'#065f46', fg:'#a7f3d0' },
];

function isDark() {
    return document.documentElement.classList.contains('dark');
}

/* ── Theme ──────────────────────────────────────────────────── */
function toggleTheme() {
    const html = document.documentElement;
    const icon = document.getElementById("theme-icon");
    const dark = html.classList.toggle('dark');
    icon.style.transition = 'transform 0.4s ease';
    icon.style.transform = 'rotate(360deg)';
    setTimeout(() => icon.style.transform = '', 400);
    icon.innerText = dark ? "☀️" : "🌙";

    // Re-render the charts if data exists so inline styles update to the new theme
    if (lastSimulationData) {
        renderResults(lastSimulationData);
    }
}

/* ── Algo change ────────────────────────────────────────────── */
function handleAlgoChange() {
    const algo = document.getElementById("algo-select").value;
    const isPriority = algo.startsWith("PRIORITY");
    const qc = document.getElementById("quantum-container");
    algo === "RR" ? qc.classList.remove("hidden") : qc.classList.add("hidden");

    const info = document.getElementById("algo-info");
    info.style.transition = "opacity .2s, transform .2s";
    info.style.opacity = "0"; info.style.transform = "translateY(4px)";
    setTimeout(() => {
        info.innerText = ALGO_INFO[algo] || "";
        info.style.opacity = "1"; info.style.transform = "translateY(0)";
    }, 180);

    const headerRow = document.getElementById("table-header-row");
    const existingPH = document.getElementById("prio-th");
    const actionH = document.getElementById("action-header");

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
    const row = document.createElement("tr");
    row.className = "row-enter hover:bg-sky-50 dark:hover:bg-cyberedge/20 transition-colors duration-150";

    const algo = document.getElementById("algo-select").value;
    const isPriority = algo.startsWith("PRIORITY");

    let html = `
        <td class="py-3 px-5 font-black text-skyaccent dark:text-cybertxt font-mono text-sm">P${currentProcessCount}</td>
        <td class="py-3 px-4"><input type="number" min="0" class="at w-20 bg-transparent text-skytext dark:text-slate-300 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="0"></td>
        <td class="py-3 px-4"><input type="number" min="1" class="bt w-20 bg-transparent text-skytext dark:text-slate-300 border-b-2 border-skyedge dark:border-cyberedge outline-none text-sm px-2 py-1 font-mono focus:border-skyaccent dark:focus:border-cybertxt transition-colors" value="1"></td>`;

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

/* ── Clear ──────────────────────────────────────────────────── */
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
        Object.keys(pidColorMap).forEach(k => delete pidColorMap[k]);
        colorCounter = 0;
        lastSimulationData = null; // Clear saved data on reset
    }, rows.length * 40 + 200);
}

function updateCounter() {
    document.getElementById("process-counter").innerText = `${currentProcessCount} / 15`;
}

/* ── Simulate ───────────────────────────────────────────────── */
async function startSimulation() {
    const rows = document.querySelectorAll("#process-table-body tr");
    if (!rows.length) { showToast("Add at least one process!"); return; }

    const algo = document.getElementById("algo-select").value;
    const quantum = algo === "RR" ? (parseInt(document.getElementById("quantum-input")?.value) || 2) : null;

    const payload = {
        algorithm: algo, quantum,
        processes: Array.from(rows).map(row => ({
            pid: row.querySelector("td:first-child").innerText.trim(),
            arrivalTime: parseInt(row.querySelector(".at").value) || 0,
            burstTime: Math.max(1, parseInt(row.querySelector(".bt").value) || 1),
            priority: row.querySelector(".priority") ? parseInt(row.querySelector(".priority").value) || 1 : 0
        }))
    };

    const btn = document.getElementById("run-btn");
    btn.innerHTML = "⏳ Running..."; btn.disabled = true; btn.style.opacity = ".7";

    try {
        const res = await fetch('/api/simulate', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
        if (!res.ok) { const e = await res.json(); showToast(e.message || "Simulation failed!"); return; }

        lastSimulationData = await res.json(); // Save data globally
        renderResults(lastSimulationData);     // Pass saved data to render

    } catch { showToast("Backend offline!"); }
    finally { btn.innerHTML = "▶ Run Simulation"; btn.disabled = false; btn.style.opacity = "1"; }
}

/* ── Render ─────────────────────────────────────────────────── */
function renderResults(data) {
    const area = document.getElementById("results-area");
    area.classList.remove("hidden");
    const lbl = document.getElementById("gantt-algo-label");
    if (lbl) lbl.innerText = data.algorithmUsed + (data.quantumUsed ? ` | Q=${data.quantumUsed}` : "");
    renderGanttChart(data.ganttBlocks);
    renderMetricsTable(data);
    setTimeout(() => area.scrollIntoView({ behavior:"smooth", block:"start" }), 120);
}

/* ── Gantt ──────────────────────────────────────────────────── */
function renderGanttChart(blocks) {
    const container = document.getElementById("gantt-container");
    container.innerHTML = "";
    if (!blocks?.length) return;

    blocks.forEach((block, i) => {
        const dur = block.end - block.start;
        const idle = block.pid === "IDLE";
        const div = document.createElement("div");
        div.className = "gantt-block" + (idle ? " idle-block" : "");
        div.style.flex = `${Math.max(dur, 0.5)} 0 auto`;
        div.style.minWidth = idle ? "36px" : "48px";

        // Set wipe delay via CSS custom property
        div.style.setProperty('--wipe-delay', `${(i * 0.09).toFixed(2)}s`);

        if (!idle) {
            if (pidColorMap[block.pid] === undefined) {
                pidColorMap[block.pid] = colorCounter % 15;
                colorCounter++;
            }
            const ci = pidColorMap[block.pid];
            // Apply colors inline so dark/light mode is guaranteed correct
            if (isDark()) {
                const c = DARK_COLORS[ci];
                div.style.background = c.bg;
                div.style.color = c.fg;
            } else {
                div.classList.add(`proc-color-${ci}`);
            }
        }

        // Shape corners
        if (i === 0) div.style.borderRadius = "12px 0 0 12px";
        if (i === blocks.length - 1) div.style.borderRadius = i === 0 ? "12px" : "0 12px 12px 0";

        div.title = idle ? `IDLE: ${block.start}→${block.end}` : `${block.pid}: ${block.start}→${block.end} (${dur}u)`;

        const label = document.createElement("span");
        label.innerText = idle ? "—" : block.pid;
        div.appendChild(label);

        const sm = document.createElement("span");
        sm.className = "time-marker"; sm.innerText = block.start;
        div.appendChild(sm);

        if (i === blocks.length - 1) {
            const em = document.createElement("span");
            em.className = "time-marker end-marker"; em.innerText = block.end;
            div.appendChild(em);
        }

        container.appendChild(div);
    });
}

/* ── Metrics table — 100% inline styles, zero Tailwind ──────── */
function renderMetricsTable(data) {
    const dark = isDark();

    // Color tokens
    const C = {
        wrap:    dark ? '#03161e' : '#f8fafc',
        border:  dark ? '#083344' : '#bae6fd',
        headBg:  dark ? '#020d14' : '#f1f5f9',
        headBdr: dark ? '#0a3344' : '#e2e8f0',
        th:      dark ? '#0e7490' : '#7dd3fc',
        divider: dark ? '#083344' : '#e2e8f0',
        rowHov:  dark ? '#071c27' : '#f0f9ff',
        footBg:  dark ? '#020d14' : '#f1f5f9',
        label:   dark ? '#0e7490' : '#94a3b8',
        muted:   dark ? '#475569' : '#94a3b8',
        ct:      dark ? '#e2e8f0' : '#0f172a',
        tat:     dark ? '#00e5ff' : '#0284c7',
        wt:      dark ? '#22d3ee' : '#0ea5e9',
        rt:      dark ? '#67e8f9' : '#38bdf8',
    };

    const th = (txt) => `<th style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:800;letter-spacing:3px;text-transform:uppercase;color:${C.th};white-space:nowrap">${txt}</th>`;

    let html = `
    <div style="background:${C.wrap};border:1px solid ${C.border};border-radius:16px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.15)">
      <div style="padding:14px 18px;border-bottom:1px solid ${C.headBdr};display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;background:${C.headBg}">
        <span style="font-family:'JetBrains Mono',monospace;font-size:10px;font-weight:800;letter-spacing:4px;text-transform:uppercase;color:${C.th}">Process Metrics</span>
        <div style="display:flex;flex-wrap:wrap;gap:14px;font-family:'JetBrains Mono',monospace;font-size:11px">
          <span style="color:${C.label}">Avg TAT: <b style="color:${C.tat}">${data.averageTAT.toFixed(2)}</b></span>
          <span style="color:${C.label}">Avg WT: <b style="color:${C.wt}">${data.averageWT.toFixed(2)}</b></span>
          <span style="color:${C.label}">Avg RT: <b style="color:${C.rt}">${data.averageRT.toFixed(2)}</b></span>
        </div>
      </div>
      <div style="overflow-x:auto">
      <table style="width:100%;border-collapse:collapse;text-align:center;min-width:500px">
        <thead>
          <tr style="background:${C.headBg};border-bottom:1px solid ${C.headBdr}">
            ${['PID','Arrival','Burst','CT','TAT','WT','RT'].map(th).join('')}
          </tr>
        </thead>
        <tbody>`;

    data.metrics.forEach((p) => {
        const ci = pidColorMap[p.pid] ?? 0;
        // PID badge colors
        let badgeBg, badgeFg;
        if (dark) {
            const c = DARK_COLORS[ci];
            badgeBg = c.bg; badgeFg = c.fg;
        } else {
            // Extract from light CSS classes by lookup
            const LIGHT = [
                {bg:'#bfdbfe',fg:'#1e3a8a'},{bg:'#bbf7d0',fg:'#14532d'},{bg:'#fde68a',fg:'#78350f'},
                {bg:'#fecdd3',fg:'#881337'},{bg:'#ddd6fe',fg:'#4c1d95'},{bg:'#fed7aa',fg:'#7c2d12'},
                {bg:'#a5f3fc',fg:'#164e63'},{bg:'#c7d2fe',fg:'#312e81'},{bg:'#fbcfe8',fg:'#831843'},
                {bg:'#a7f3d0',fg:'#065f46'},{bg:'#fef08a',fg:'#713f12'},{bg:'#99f6e4',fg:'#134e4a'},
                {bg:'#e9d5ff',fg:'#581c87'},{bg:'#bae6fd',fg:'#0c4a6e'},{bg:'#fca5a5',fg:'#7f1d1d'}
            ];
            badgeBg = LIGHT[ci].bg; badgeFg = LIGHT[ci].fg;
        }

        const cell = (val, color, bold=false) =>
            `<td style="padding:10px 14px;font-family:'JetBrains Mono',monospace;color:${color};${bold?'font-weight:800;':''}">${val}</td>`;

        html += `
          <tr class="metric-row"
              style="border-bottom:1px solid ${C.divider}"
              onmouseover="this.style.background='${C.rowHov}'"
              onmouseout="this.style.background='transparent'">
            <td style="padding:10px 14px">
              <span style="background:${badgeBg};color:${badgeFg};padding:3px 12px;border-radius:6px;font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:800;display:inline-block">${p.pid}</span>
            </td>
            ${cell(p.arrivalTime, C.muted)}
            ${cell(p.burstTime, C.muted)}
            ${cell(p.completionTime, C.ct, true)}
            ${cell(p.turnaroundTime, C.tat, true)}
            ${cell(p.waitingTime, C.wt, true)}
            ${cell(p.responseTime, C.rt, true)}
          </tr>`;
    });

    html += `
        </tbody>
        <tfoot>
          <tr style="background:${C.footBg};border-top:1px solid ${C.border}">
            <td colspan="4" style="padding:10px 14px;text-align:right;font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:800;letter-spacing:3px;text-transform:uppercase;color:${C.th}">Average</td>
            <td style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800;color:${C.tat}">${data.averageTAT.toFixed(2)}</td>
            <td style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800;color:${C.wt}">${data.averageWT.toFixed(2)}</td>
            <td style="padding:10px 14px;font-family:'JetBrains Mono',monospace;font-weight:800;color:${C.rt}">${data.averageRT.toFixed(2)}</td>
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
    t.style.cssText = `font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:700;padding:10px 20px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.3);background:${isDark()?'#00e5ff':'#0c4a6e'};color:${isDark()?'#030d12':'#fff'}`;
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
    setTimeout(() => addProcessRow(), 100);
    setTimeout(() => addProcessRow(), 200);
    setTimeout(() => addProcessRow(), 300);
    handleAlgoChange();
});