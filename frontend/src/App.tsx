import { useEffect, useMemo, useState } from "react";

type EventItem = {
  id: number;
  title: string;
  location?: string | null;
  startTime: string;
  endTime: string;
};

type Task = {
  id: number;
  title: string;
  deadline?: string | null;
  estMinutes: number;
  priority: number;
  status: string;
};

type Block = {
  id: number;
  planId: number;
  taskId?: number | null;
  title: string;
  startTime: string;
  endTime: string;
  status: string;
};

type DayResponse = {
  date: string;
  weekStart: string;
  hasPlan: boolean;
  openTasksCount: number;
  events: EventItem[];
  blocks: Block[];
};

type Preferences = {
  userId: number;
  dayStartHour: number;
  dayEndHour: number;
  blockMinutes: number;
  updatedAt: string;
};

type Tab = "today" | "events" | "tasks" | "plan" | "settings";

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function computeMondayIso(d = new Date()) {
  const day = d.getDay(); // 0=Sun
  const diffToMon = (day === 0 ? -6 : 1) - day;
  const mon = new Date(d);
  mon.setDate(d.getDate() + diffToMon);
  mon.setHours(0, 0, 0, 0);
  return mon.toISOString().slice(0, 10);
}

export default function App() {
  const [tab, setTab] = useState<Tab>("today");
  const [status, setStatus] = useState("loading...");
  const [msg, setMsg] = useState("");

  // Events import
  const [icsFile, setIcsFile] = useState<File | null>(null);
  const [events, setEvents] = useState<EventItem[]>([]);

  // Tasks
  const [tasks, setTasks] = useState<Task[]>([]);
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDeadlineLocal, setTaskDeadlineLocal] = useState("");
  const [taskEst, setTaskEst] = useState(60);
  const [taskPriority, setTaskPriority] = useState(1);

  // Plan
  const [weekStart, setWeekStart] = useState(() => computeMondayIso());
  const [planBlocks, setPlanBlocks] = useState<Block[]>([]);
  const [planStats, setPlanStats] = useState<{ scheduledBlocks: number; unscheduledTasks: number } | null>(null);

  // Today
  const [dayDate, setDayDate] = useState(todayIsoDate());
  const [dayData, setDayData] = useState<DayResponse | null>(null);

  // Settings / Preferences
  const [prefs, setPrefs] = useState<Preferences | null>(null);
  const [prefDayStartHour, setPrefDayStartHour] = useState(9);
  const [prefDayEndHour, setPrefDayEndHour] = useState(18);
  const [prefBlockMinutes, setPrefBlockMinutes] = useState(45);

  const loadEvents = async () => {
    const res = await fetch("/api/events");
    setEvents(await res.json());
  };

  const loadTasks = async () => {
    const res = await fetch("/api/tasks");
    setTasks(await res.json());
  };

  const loadDay = async (date: string) => {
    const res = await fetch(`/api/day?date=${encodeURIComponent(date)}`);
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Load day failed (${res.status}): ${err}`);
      return;
    }
    const out = (await res.json()) as DayResponse;
    setDayData(out);

    // keep weekStart in sync with chosen day (nice UX)
    if (out.weekStart) setWeekStart(out.weekStart);
  };

  const loadPrefs = async () => {
    const res = await fetch("/api/preferences");
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Load preferences failed (${res.status}): ${err}`);
      return;
    }
    const out = (await res.json()) as Preferences;
    setPrefs(out);
    setPrefDayStartHour(out.dayStartHour);
    setPrefDayEndHour(out.dayEndHour);
    setPrefBlockMinutes(out.blockMinutes);
  };

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setStatus(d.status))
      .catch(() => setStatus("error"));

    loadEvents();
    loadTasks();
    loadDay(dayDate);
    loadPrefs();
  }, []);

  useEffect(() => {
    loadDay(dayDate);
  }, [dayDate]);

  const importIcs = async () => {
    if (!icsFile) {
      setMsg("Please choose an .ics file first.");
      return;
    }
    setMsg("Importing...");
    const form = new FormData();
    form.append("file", icsFile);

    const res = await fetch("/api/feeds/1/import", { method: "POST", body: form });
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Import failed (${res.status}): ${err}`);
      return;
    }
    const out = await res.json();
    setMsg(`Imported ${out.imported} events.`);
    await loadEvents();
    await loadDay(dayDate);
  };

  const addTask = async () => {
    if (!taskTitle.trim()) {
      setMsg("Task title is required.");
      return;
    }

    const deadlineIso = taskDeadlineLocal.trim().length > 0 ? new Date(taskDeadlineLocal).toISOString() : null;

    const res = await fetch("/api/tasks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: taskTitle,
        deadline: deadlineIso,
        estMinutes: taskEst,
        priority: taskPriority,
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Create task failed (${res.status}): ${err}`);
      return;
    }

    setTaskTitle("");
    setTaskDeadlineLocal("");
    setTaskEst(60);
    setTaskPriority(1);
    setMsg("Task created.");
    await loadTasks();
    await loadDay(dayDate);
  };

  const markTaskDone = async (id: number) => {
    const res = await fetch(`/api/tasks/${id}/done`, { method: "POST" });
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Mark done failed (${res.status}): ${err}`);
      return;
    }
    setMsg("Task marked done.");
    await loadTasks();
    await loadDay(dayDate);
  };

  const generatePlan = async (ws: string) => {
    setMsg("Generating plan...");
    const res = await fetch(`/api/plans/generate?weekStart=${encodeURIComponent(ws)}`, { method: "POST" });
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Generate failed (${res.status}): ${err}`);
      return;
    }
    const out = await res.json();
    setPlanBlocks(out.blocks ?? []);
    setPlanStats({ scheduledBlocks: out.scheduledBlocks, unscheduledTasks: out.unscheduledTasks });
    setMsg(`Plan generated. Blocks: ${out.scheduledBlocks}, Unscheduled tasks: ${out.unscheduledTasks}`);
    await loadDay(dayDate);
  };

  const replanWeek = async (ws: string) => {
    setMsg("Replanning...");
    const res = await fetch(`/api/replan?weekStart=${encodeURIComponent(ws)}`, { method: "POST" });
    if (!res.ok) {
      const err = await res.text();
      setMsg(`Replan failed (${res.status}): ${err}`);
      return;
    }
    const out = await res.json();
    setPlanBlocks(out.blocks ?? []);
    setPlanStats({ scheduledBlocks: out.scheduledBlocks, unscheduledTasks: out.unscheduledTasks });
    setMsg(`Replanned. Blocks: ${out.scheduledBlocks}, Unscheduled tasks: ${out.unscheduledTasks}`);
    await loadDay(dayDate);
  };

  const updatePrefs = async () => {
    setMsg("Saving preferences...");
    const res = await fetch("/api/preferences", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        dayStartHour: prefDayStartHour,
        dayEndHour: prefDayEndHour,
        blockMinutes: prefBlockMinutes,
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Save preferences failed (${res.status}): ${err}`);
      return;
    }

    const out = (await res.json()) as Preferences;
    setPrefs(out);
    setMsg("Preferences saved. Generate/Replan to apply to the schedule.");
  };

  const updateBlockStatus = async (blockId: number, status: "DONE" | "SKIPPED" | "PLANNED") => {
    const res = await fetch(`/api/blocks/${blockId}/status`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Update block failed (${res.status}): ${err}`);
      return;
    }

    setMsg(`Block marked ${status}.`);
    await loadDay(dayDate);
  };

  const todayTimeline = useMemo(() => {
    if (!dayData) return [];
    const items = [
      ...dayData.events.map((e) => ({
        type: "event" as const,
        id: e.id,
        title: e.title,
        start: e.startTime,
        end: e.endTime,
        extra: e.location ?? "",
      })),
      ...dayData.blocks.map((b) => ({
        type: "block" as const,
        id: b.id,
        title: b.title,
        start: b.startTime,
        end: b.endTime,
        extra: b.status,
      })),
    ];
    return items.sort((a, b) => +new Date(a.start) - +new Date(b.start));
  }, [dayData]);

  const exportUrl = `/api/export/week?weekStart=${encodeURIComponent(weekStart)}`;

  return (
    <div style={{ fontFamily: "system-ui", padding: 24, maxWidth: 1100 }}>
      <h1>TimeBlocker Pro</h1>
      <p>
        Backend status: <b>{status}</b>
      </p>

      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        <button onClick={() => setTab("today")} style={{ padding: "8px 12px" }}>
          Today
        </button>
        <button onClick={() => setTab("events")} style={{ padding: "8px 12px" }}>
          Events
        </button>
        <button onClick={() => setTab("tasks")} style={{ padding: "8px 12px" }}>
          Tasks
        </button>
        <button onClick={() => setTab("plan")} style={{ padding: "8px 12px" }}>
          Plan
        </button>
        <button onClick={() => setTab("settings")} style={{ padding: "8px 12px" }}>
          Settings
        </button>

        <button
          onClick={() => {
            loadEvents();
            loadTasks();
            loadDay(dayDate);
            loadPrefs();
          }}
          style={{ padding: "8px 12px", marginLeft: "auto" }}
        >
          Refresh
        </button>
      </div>

      {msg && <p>{msg}</p>}

      {tab === "today" && (
        <>
          <h2>Day view</h2>

          <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <label style={{ fontSize: 13 }}>
              Date
              <input
                type="date"
                value={dayDate}
                onChange={(e) => setDayDate(e.target.value)}
                style={{ padding: 8, marginLeft: 8 }}
              />
            </label>

            {dayData?.weekStart && (
              <>
                <button onClick={() => generatePlan(dayData.weekStart)} style={{ padding: "8px 12px" }}>
                  Generate week plan
                </button>
                <button onClick={() => replanWeek(dayData.weekStart)} style={{ padding: "8px 12px" }}>
                  Replan week
                </button>
              </>
            )}
          </div>

          {dayData && (
            <p style={{ marginTop: 10 }}>
              Open tasks: <b>{dayData.openTasksCount}</b> | Plan exists: <b>{dayData.hasPlan ? "Yes" : "No"}</b>
              {prefs && (
                <>
                  {" "}
                  | Window: <b>{prefs.dayStartHour}:00–{prefs.dayEndHour}:00</b> | Block: <b>{prefs.blockMinutes}m</b>
                </>
              )}
            </p>
          )}

          <h3 style={{ marginTop: 16 }}>Timeline</h3>
          {!dayData ? (
            <p>Loading…</p>
          ) : todayTimeline.length === 0 ? (
            <p>No events or blocks on this date.</p>
          ) : (
            <ul>
              {todayTimeline.map((it) => (
                <li key={`${it.type}-${it.id}`} style={{ marginBottom: 10 }}>
                  <b>{it.type === "event" ? "Event" : "Block"}:</b> {it.title}
                  <div style={{ fontSize: 13 }}>
                    {new Date(it.start).toLocaleString()} → {new Date(it.end).toLocaleString()}
                    {it.extra ? ` | ${it.extra}` : ""}
                  </div>

                  {it.type === "block" && (
                    <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                      <button onClick={() => updateBlockStatus(it.id, "DONE")} style={{ padding: "6px 10px" }}>
                        Done
                      </button>
                      <button onClick={() => updateBlockStatus(it.id, "SKIPPED")} style={{ padding: "6px 10px" }}>
                        Skip
                      </button>
                      <button onClick={() => updateBlockStatus(it.id, "PLANNED")} style={{ padding: "6px 10px" }}>
                        Reset
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {tab === "events" && (
        <>
          <h2>Import timetable (.ics)</h2>
          <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
            <input type="file" accept=".ics,text/calendar" onChange={(e) => setIcsFile(e.target.files?.[0] ?? null)} />
            <button onClick={importIcs} style={{ padding: "8px 12px" }}>
              Import
            </button>
          </div>

          <h2 style={{ marginTop: 20 }}>Events</h2>
          {events.length === 0 ? (
            <p>No events yet.</p>
          ) : (
            <ul>
              {events.slice(0, 50).map((ev) => (
                <li key={ev.id} style={{ marginBottom: 8 }}>
                  <b>{ev.title}</b>
                  <div style={{ fontSize: 13 }}>
                    {new Date(ev.startTime).toLocaleString()} → {new Date(ev.endTime).toLocaleString()}
                    {ev.location ? ` | ${ev.location}` : ""}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {events.length > 50 && <p>Showing first 50 events.</p>}
        </>
      )}

      {tab === "tasks" && (
        <>
          <h2>Tasks</h2>

          <div style={{ display: "grid", gap: 8, maxWidth: 520 }}>
            <input
              value={taskTitle}
              onChange={(e) => setTaskTitle(e.target.value)}
              placeholder="Task title (e.g., Write CA report)"
              style={{ padding: 8 }}
            />

            <label style={{ fontSize: 13 }}>
              Deadline (optional)
              <input
                type="datetime-local"
                value={taskDeadlineLocal}
                onChange={(e) => setTaskDeadlineLocal(e.target.value)}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <label style={{ fontSize: 13 }}>
              Estimate minutes
              <input
                type="number"
                min={1}
                value={taskEst}
                onChange={(e) => setTaskEst(parseInt(e.target.value || "60", 10))}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <label style={{ fontSize: 13 }}>
              Priority (0–5)
              <input
                type="number"
                min={0}
                max={5}
                value={taskPriority}
                onChange={(e) => setTaskPriority(parseInt(e.target.value || "1", 10))}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <button onClick={addTask} style={{ padding: "8px 12px" }}>
              Add Task
            </button>
          </div>

          <h3 style={{ marginTop: 20 }}>Open Tasks</h3>
          {tasks.length === 0 ? (
            <p>No tasks yet.</p>
          ) : (
            <ul>
              {tasks.map((t) => (
                <li key={t.id} style={{ marginBottom: 10 }}>
                  <b>{t.title}</b>{" "}
                  <span style={{ fontSize: 13 }}>
                    (est {t.estMinutes}m, prio {t.priority}
                    {t.deadline ? `, due ${new Date(t.deadline).toLocaleString()}` : ""})
                  </span>
                  <div>
                    <button onClick={() => markTaskDone(t.id)} style={{ padding: "6px 10px", marginTop: 4 }}>
                      Mark done
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {tab === "plan" && (
        <>
          <h2>Weekly plan</h2>

          <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <label style={{ fontSize: 13 }}>
              Week start (Monday)
              <input
                type="date"
                value={weekStart}
                onChange={(e) => setWeekStart(e.target.value)}
                style={{ padding: 8, marginLeft: 8 }}
              />
            </label>

            <button onClick={() => generatePlan(weekStart)} style={{ padding: "8px 12px" }}>
              Generate plan
            </button>

            <button onClick={() => replanWeek(weekStart)} style={{ padding: "8px 12px" }}>
              Replan week
            </button>

            <a href={exportUrl} style={{ padding: "8px 12px", display: "inline-block" }}>
              Export week (.ics)
            </a>
          </div>

          {planStats && (
            <p style={{ marginTop: 10 }}>
              Blocks scheduled: <b>{planStats.scheduledBlocks}</b> | Unscheduled tasks:{" "}
              <b>{planStats.unscheduledTasks}</b>
            </p>
          )}

          <h3 style={{ marginTop: 16 }}>Blocks</h3>
          {planBlocks.length === 0 ? (
            <p>No plan yet.</p>
          ) : (
            <ul>
              {planBlocks.slice(0, 200).map((b) => (
                <li key={b.id} style={{ marginBottom: 8 }}>
                  <b>{b.title}</b> <span style={{ fontSize: 13 }}>({b.status})</span>
                  <div style={{ fontSize: 13 }}>
                    {new Date(b.startTime).toLocaleString()} → {new Date(b.endTime).toLocaleString()}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {planBlocks.length > 200 && <p>Showing first 200 blocks.</p>}
        </>
      )}

      {tab === "settings" && (
        <>
          <h2>Settings</h2>

          <p style={{ fontSize: 13, maxWidth: 760 }}>
            These settings control how the planner schedules tasks. After saving, click “Generate plan” or “Replan week”
            to apply the new rules.
          </p>

          <div style={{ display: "grid", gap: 10, maxWidth: 420 }}>
            <label style={{ fontSize: 13 }}>
              Day start hour (0–23)
              <input
                type="number"
                min={0}
                max={23}
                value={prefDayStartHour}
                onChange={(e) => setPrefDayStartHour(parseInt(e.target.value || "0", 10))}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <label style={{ fontSize: 13 }}>
              Day end hour (0–23)
              <input
                type="number"
                min={0}
                max={23}
                value={prefDayEndHour}
                onChange={(e) => setPrefDayEndHour(parseInt(e.target.value || "0", 10))}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <label style={{ fontSize: 13 }}>
              Block minutes (15–240)
              <input
                type="number"
                min={15}
                max={240}
                value={prefBlockMinutes}
                onChange={(e) => setPrefBlockMinutes(parseInt(e.target.value || "15", 10))}
                style={{ padding: 8, width: "100%" }}
              />
            </label>

            <button onClick={updatePrefs} style={{ padding: "8px 12px" }}>
              Save settings
            </button>
          </div>

          {prefs && (
            <p style={{ marginTop: 12, fontSize: 13 }}>
              Current saved settings: <b>{prefs.dayStartHour}:00–{prefs.dayEndHour}:00</b>, block{" "}
              <b>{prefs.blockMinutes}m</b> (updated {new Date(prefs.updatedAt).toLocaleString()}).
            </p>
          )}
        </>
      )}
    </div>
  );
}
