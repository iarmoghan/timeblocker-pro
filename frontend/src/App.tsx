import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { formatNiceDateTime, formatNiceRange, formatNiceTime } from "./utils/datetime";

// Frontend TypeScript types.
// These describe the shape of the JSON data returned by the backend.
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

type UnscheduledTask = {
  taskId: number;
  title: string;
  remainingMinutes: number;
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

// The app has four main screens.
type Tab = "dashboard" | "tasks" | "planner" | "settings";

// Converts a JavaScript Date into yyyy-mm-dd format for date inputs and API calls.
function toLocalIsoDate(d = new Date()) {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function todayIsoDate() {
  return toLocalIsoDate(new Date());
}

// Finds the Monday for the selected week.
// The backend planner uses a weekStart date when generating a plan.
function computeMondayIso(d = new Date()) {
  const day = d.getDay();
  const diffToMon = (day === 0 ? -6 : 1) - day;
  const mon = new Date(d);
  mon.setDate(d.getDate() + diffToMon);
  mon.setHours(0, 0, 0, 0);
  return toLocalIsoDate(mon);
}

// Converts numeric priority into a user-friendly label.
function priorityLabel(priority: number) {
  if (priority >= 4) return "High";
  if (priority >= 2) return "Medium";
  return "Low";
}

function priorityColor(priority: number) {
  if (priority >= 4) return { bg: "#3b1d1d", border: "#7f2d2d", text: "#fecaca" };
  if (priority >= 2) return { bg: "#3b2f12", border: "#7c5d12", text: "#fde68a" };
  return { bg: "#123524", border: "#1f6b45", text: "#bbf7d0" };
}

// Chooses badge colours based on block status.
function statusColor(status: string) {
  if (status === "DONE") return { bg: "#123524", border: "#1f6b45", text: "#bbf7d0" };
  if (status === "SKIPPED") return { bg: "#3b2f12", border: "#7c5d12", text: "#fde68a" };
  if (status === "PLANNED") return { bg: "#172554", border: "#1d4ed8", text: "#bfdbfe" };
  return { bg: "#27272a", border: "#52525b", text: "#e4e4e7" };
}

// Inline styles for the frontend.
// In a larger version I would split this into CSS/components, but it keeps this prototype self-contained.
const styles: Record<string, CSSProperties> = {
  page: {
    minHeight: "100vh",
    background: "linear-gradient(135deg, #111827 0%, #18181b 45%, #0f172a 100%)",
    color: "#f4f4f5",
    fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  },
  shell: {
    width: "100%",
    maxWidth: 1440,
    margin: "0 auto",
    padding: "28px 32px 56px",
  },
  topBar: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 16,
    marginBottom: 22,
  },
  title: {
    fontSize: 42,
    lineHeight: 1.05,
    margin: 0,
    letterSpacing: "-0.04em",
  },
  subtitle: {
    margin: "10px 0 0",
    color: "#a1a1aa",
    fontSize: 16,
  },
  nav: {
    display: "flex",
    gap: 8,
    flexWrap: "wrap",
    marginBottom: 22,
    padding: 6,
    border: "1px solid #27272a",
    borderRadius: 16,
    background: "rgba(24, 24, 27, 0.72)",
    width: "fit-content",
  },
  navButton: {
    border: "1px solid transparent",
    background: "transparent",
    color: "#d4d4d8",
    padding: "10px 14px",
    borderRadius: 12,
    cursor: "pointer",
    fontWeight: 650,
  },
  navButtonActive: {
    background: "#2563eb",
    color: "white",
    boxShadow: "0 10px 24px rgba(37, 99, 235, 0.25)",
  },
  card: {
    border: "1px solid #27272a",
    background: "rgba(24, 24, 27, 0.74)",
    borderRadius: 18,
    padding: 18,
    boxShadow: "0 18px 50px rgba(0,0,0,0.22)",
  },
  cardSoft: {
    border: "1px solid #27272a",
    background: "rgba(39, 39, 42, 0.52)",
    borderRadius: 16,
    padding: 16,
  },
  sectionHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 12,
    marginBottom: 14,
  },
  h2: {
    margin: 0,
    fontSize: 26,
    letterSpacing: "-0.02em",
  },
  h3: {
    margin: "0 0 12px",
    fontSize: 18,
  },
  muted: {
    color: "#a1a1aa",
  },
  grid4: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(185px, 1fr))",
    gap: 12,
  },
  twoCol: {
    display: "grid",
    gridTemplateColumns: "minmax(420px, 1fr) minmax(420px, 1fr)",
    gap: 20,
    alignItems: "start",
  },
  button: {
    border: "1px solid #3f3f46",
    background: "#18181b",
    color: "#f4f4f5",
    padding: "10px 13px",
    borderRadius: 12,
    cursor: "pointer",
    fontWeight: 650,
  },
  primaryButton: {
    border: "1px solid #2563eb",
    background: "#2563eb",
    color: "white",
  },
  dangerButton: {
    border: "1px solid #7f1d1d",
    background: "#3b1d1d",
    color: "#fecaca",
  },
  input: {
    background: "#09090b",
    color: "#f4f4f5",
    border: "1px solid #3f3f46",
    borderRadius: 12,
    padding: 11,
    width: "100%",
    boxSizing: "border-box",
  },
  label: {
    fontSize: 13,
    color: "#d4d4d8",
    display: "grid",
    gap: 6,
  },
  badge: {
    display: "inline-flex",
    alignItems: "center",
    width: "fit-content",
    borderRadius: 999,
    border: "1px solid #52525b",
    padding: "4px 9px",
    fontSize: 12,
    fontWeight: 750,
    letterSpacing: "0.02em",
  },
  timelineRow: {
    display: "grid",
    gridTemplateColumns: "120px minmax(0, 1fr)",
    gap: 14,
    padding: "14px 0",
    borderBottom: "1px solid #27272a",
  },
  timelineTime: {
    color: "#a1a1aa",
    fontSize: 13,
    lineHeight: 1.35,
  },
  formGrid: {
    display: "grid",
    gap: 12,
  },
};

// Small reusable badge component used for priorities, events and block statuses.
function Badge({ label, color }: { label: string; color?: { bg: string; border: string; text: string } }) {
  return (
    <span
      style={{
        ...styles.badge,
        background: color?.bg ?? "#27272a",
        borderColor: color?.border ?? "#52525b",
        color: color?.text ?? "#e4e4e7",
      }}
    >
      {label}
    </span>
  );
}

export default function App() {
  // Main UI state for navigation and user messages.
  // tab controls which screen is currently visible. 
  const [tab, setTab] = useState<Tab>("dashboard");
  const [status, setStatus] = useState("loading...");
  const [msg, setMsg] = useState("");

  // Timetable import state. The selected .ics file is uploaded to the backend.
  const [icsFile, setIcsFile] = useState<File | null>(null);
  const [events, setEvents] = useState<EventItem[]>([]);

  // Task form and task list state.
  const [tasks, setTasks] = useState<Task[]>([]);
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDeadlineLocal, setTaskDeadlineLocal] = useState("");
  const [taskEst, setTaskEst] = useState(60);
  const [taskPriority, setTaskPriority] = useState(1);

  // Weekly plan state. weekStart is the Monday used by the backend planner.
  const [weekStart, setWeekStart] = useState(() => computeMondayIso());
  const [planBlocks, setPlanBlocks] = useState<Block[]>([]);
  const [planStats, setPlanStats] = useState<{
    scheduledBlocks: number;
    unscheduledTasks: number;
    unscheduled: UnscheduledTask[];
  } | null>(null);

  // Dashboard day view state. This lets the user choose a specific date to inspect.
  const [dayDate, setDayDate] = useState(todayIsoDate());
  const [dayData, setDayData] = useState<DayResponse | null>(null);

  // User preference state for study hours and block size.
  const [prefs, setPrefs] = useState<Preferences | null>(null);
  const [prefDayStartHour, setPrefDayStartHour] = useState(9);
  const [prefDayEndHour, setPrefDayEndHour] = useState(18);
  const [prefBlockMinutes, setPrefBlockMinutes] = useState(45);

  // Loads imported timetable events from the backend.
  const loadEvents = async () => {
    const res = await fetch("/api/events");
    setEvents(await res.json());
  };

  // Loads all tasks from the backend.
  const loadTasks = async () => {
    const res = await fetch("/api/tasks");
    setTasks(await res.json());
  };

  // Loads dashboard data for one selected day.
  // This includes fixed events and generated study blocks.
  const loadDay = async (date: string) => {
    const res = await fetch(`/api/day?date=${encodeURIComponent(date)}`);

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Load day failed (${res.status}): ${err}`);
      return;
    }

    const out = (await res.json()) as DayResponse;
    setDayData(out);

    if (out.weekStart) {
      setWeekStart(out.weekStart);
    }
  };

  // Loads saved study preferences such as start hour, end hour and block size.
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

  // Refreshes all main data from the backend.
  // This is used by the Sync data button.
  const refreshAll = async () => {
    await Promise.all([loadEvents(), loadTasks(), loadDay(dayDate), loadPrefs()]);
  };

  // This runs once when the app first loads.
  // It checks the backend health and loads the initial data.
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

  // Whenever the selected day changes, reload that day from the backend.
  useEffect(() => {
    loadDay(dayDate);
  }, [dayDate]);

  // Uploads the selected .ics timetable file to the backend.
  // The backend parses it and stores timetable events as fixed busy time.
  const importIcs = async () => {
    if (!icsFile) {
      setMsg("Please choose an .ics file first.");
      return;
    }

    setMsg("Importing timetable...");

    const form = new FormData();
    form.append("file", icsFile);

    const res = await fetch("/api/feeds/1/import", {
      method: "POST",
      body: form,
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Import failed (${res.status}): ${err}`);
      return;
    }

    const out = await res.json();

    setMsg(`Imported ${out.imported} events from ${icsFile.name}.`);
    await loadEvents();
    await loadDay(dayDate);
  };

  // Creates a task by sending the form data to TaskController.
  // The backend stores it as an OPEN task so it can be scheduled.
  const addTask = async () => {
    if (!taskTitle.trim()) {
      setMsg("Task title is required.");
      return;
    }

    if (taskEst < 1) {
      setMsg("Estimate minutes must be at least 1.");
      return;
    }

    if (taskPriority < 0 || taskPriority > 5) {
      setMsg("Priority must be between 0 and 5.");
      return;
    }

    const deadlineIso = taskDeadlineLocal.trim().length > 0 ? new Date(taskDeadlineLocal).toISOString() : null;

    const res = await fetch("/api/tasks", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
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

  // Manually marks a task as done.
  // The backend also updates linked blocks so the timeline stays consistent.
  const markTaskDone = async (id: number) => {
    const res = await fetch(`/api/tasks/${id}/done`, {
      method: "POST",
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Mark task done failed (${res.status}): ${err}`);
      return;
    }

    setMsg("Task marked done.");
    await loadTasks();
    await loadDay(dayDate);
  };

  // Calls the backend to generate a weekly plan for the selected week.
  // The actual scheduling algorithm is in PlanService, not in the frontend.
  const generatePlan = async (ws: string) => {
    setMsg("Generating plan...");

    const res = await fetch(`/api/plans/generate?weekStart=${encodeURIComponent(ws)}`, {
      method: "POST",
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Generate failed (${res.status}): ${err}`);
      return;
    }

    const out = await res.json();

    setPlanBlocks(out.blocks ?? []);
    setPlanStats({
      scheduledBlocks: out.scheduledBlocks,
      unscheduledTasks: out.unscheduledTasks,
      unscheduled: out.unscheduled ?? [],
    });

    setMsg(
      `Plan generated. Blocks: ${out.scheduledBlocks}, unscheduled tasks: ${out.unscheduledTasks}.`
    );

    await loadDay(dayDate);
    await loadTasks();
  };

  // Calls the backend to replan the week after progress changes.
  // DONE blocks are kept fixed by the backend and remaining work is rescheduled.
  const replanWeek = async (ws: string) => {
    setMsg("Replanning week...");

    const res = await fetch(`/api/replan?weekStart=${encodeURIComponent(ws)}`, {
      method: "POST",
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Replan failed (${res.status}): ${err}`);
      return;
    }

    const out = await res.json();

    setPlanBlocks(out.blocks ?? []);
    setPlanStats({
      scheduledBlocks: out.scheduledBlocks,
      unscheduledTasks: out.unscheduledTasks,
      unscheduled: out.unscheduled ?? [],
    });

    setMsg(
      `Replan complete. DONE blocks were kept fixed. Remaining work was rescheduled. Unscheduled tasks: ${out.unscheduledTasks}.`
    );

    await loadDay(dayDate);
    await loadTasks();
  };

  // Saves study preferences to the backend.
  // These settings control the planner window and block size.
  const updatePrefs = async () => {
    if (prefDayStartHour < 0 || prefDayStartHour > 23 || prefDayEndHour < 0 || prefDayEndHour > 23) {
      setMsg("Day start and end must be between 0 and 23.");
      return;
    }

    if (prefDayStartHour >= prefDayEndHour) {
      setMsg("Day start must be earlier than day end.");
      return;
    }

    if (prefBlockMinutes < 15 || prefBlockMinutes > 240) {
      setMsg("Block minutes must be between 15 and 240.");
      return;
    }

    setMsg("Saving preferences...");

    const res = await fetch("/api/preferences", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
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
    setMsg("Preferences saved. Generate or replan to apply them.");
  };

  // Updates a study block status when the user clicks Done, Skip or Reset.
  // The backend may also update the parent task based on completed minutes.
  const updateBlockStatus = async (blockId: number, status: "DONE" | "SKIPPED" | "PLANNED") => {
    const res = await fetch(`/api/blocks/${blockId}/status`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ status }),
    });

    if (!res.ok) {
      const err = await res.text();
      setMsg(`Update block failed (${res.status}): ${err}`);
      return;
    }

    setMsg(`Block marked ${status}.`);

    await loadDay(dayDate);
    await loadTasks();
  };

  // Combines fixed events and generated study blocks into one sorted timeline.
  // useMemo avoids recalculating unless dayData changes.
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

  // Split tasks into open and completed lists for display.
  const openTasks = tasks.filter((t) => t.status !== "DONE");
  const doneTasks = tasks.filter((t) => t.status === "DONE");

  const todayEventsCount = dayData?.events.length ?? 0;
  const todayBlocksCount = dayData?.blocks.length ?? 0;
  const todayDoneBlocksCount = dayData?.blocks.filter((b) => b.status === "DONE").length ?? 0;
  const todayPlannedBlocksCount = dayData?.blocks.filter((b) => b.status === "PLANNED").length ?? 0;

  // Used to find the next upcoming timeline item.
  const selectedDayReferenceTime =
    dayDate === todayIsoDate() ? Date.now() : new Date(`${dayDate}T00:00:00`).getTime();

  const nextTimelineItem = todayTimeline.find(
    (item) => new Date(item.end).getTime() > selectedDayReferenceTime
  );

  const currentWeekStart = dayData?.weekStart ?? weekStart;
  const exportUrl = `/api/export/week?weekStart=${encodeURIComponent(weekStart)}`;

  // Reusable navigation button for switching between Dashboard, Tasks, Planner and Settings.
  const NavButton = ({ id, label }: { id: Tab; label: string }) => (
    <button
      onClick={() => setTab(id)}
      style={{
        ...styles.navButton,
        ...(tab === id ? styles.navButtonActive : {}),
      }}
    >
      {label}
    </button>
  );

  // Timeline component used to show both timetable events and planned study blocks.
  const Timeline = ({ limit }: { limit?: number }) => {
    const visibleItems = limit ? todayTimeline.slice(0, limit) : todayTimeline;

    if (!dayData) {
      return <p style={styles.muted}>Loading timeline…</p>;
    }

    if (todayTimeline.length === 0) {
      return <p style={styles.muted}>No timetable events or planned study blocks for this date yet.</p>;
    }

    return (
      <div>
        {visibleItems.map((it) => {
          const isBlock = it.type === "block";
          const color = isBlock
            ? statusColor(it.extra)
            : { bg: "#1e293b", border: "#334155", text: "#bfdbfe" };

          return (
            <div key={`${it.type}-${it.id}`} style={styles.timelineRow}>
              <div style={styles.timelineTime}>
                <b>{formatNiceTime(it.start)}</b>
                <br />
                {formatNiceTime(it.end)}
              </div>

              <div>
                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <Badge label={it.type === "event" ? "EVENT" : it.extra} color={color} />
                  <b>{it.title}</b>
                </div>

                <div style={{ marginTop: 6, fontSize: 13, color: "#a1a1aa" }}>
                  {formatNiceRange(it.start, it.end)}
                  {it.extra && it.type === "event" ? ` | ${it.extra}` : ""}
                </div>

                {isBlock && (
                  <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
                    <button onClick={() => updateBlockStatus(it.id, "DONE")} style={styles.button}>
                      Done
                    </button>
                    <button onClick={() => updateBlockStatus(it.id, "SKIPPED")} style={styles.button}>
                      Skip
                    </button>
                    <button onClick={() => updateBlockStatus(it.id, "PLANNED")} style={styles.button}>
                      Reset
                    </button>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {limit && todayTimeline.length > limit && (
          <p style={{ marginTop: 12 }}>
            Showing first {limit} items.{" "}
            <button onClick={() => setTab("dashboard")} style={styles.button}>
              View dashboard
            </button>
          </p>
        )}
      </div>
    );
  };

  return (
    <div style={styles.page}>
      <main style={styles.shell}>
        <div style={styles.topBar}>
          <div>
            <h1 style={styles.title}>TimeBlocker Pro</h1>
            <p style={styles.subtitle}>Plan smarter. Replan faster. Keep your study week realistic.</p>
          </div>

          <button onClick={refreshAll} style={styles.button}>
            Sync data
          </button>
        </div>

        <nav style={styles.nav}>
          <NavButton id="dashboard" label="Dashboard" />
          <NavButton id="tasks" label="Tasks" />
          <NavButton id="planner" label="Planner" />
          <NavButton id="settings" label="Settings" />
        </nav>

        {msg && (
          <div style={{ ...styles.cardSoft, marginBottom: 16, borderColor: "#334155" }}>
            <b>Status:</b> {msg}
          </div>
        )}

        {/* Dashboard screen: shows day summary, timeline, quick actions and next item. */}
        {tab === "dashboard" && (
          <>
            <div style={styles.sectionHeader}>
              <div>
                <h2 style={styles.h2}>Dashboard</h2>
                <p style={{ ...styles.muted, margin: "6px 0 0" }}>
                  A quick view of your day, tasks, and study plan.
                </p>
              </div>

              <label style={{ ...styles.label, maxWidth: 180 }}>
                Selected date
                <input
                  type="date"
                  value={dayDate}
                  onChange={(e) => setDayDate(e.target.value)}
                  style={styles.input}
                />
              </label>
            </div>

            <div style={{ ...styles.grid4, marginBottom: 16 }}>
              <div style={styles.card}>
                <div style={styles.muted}>Open tasks</div>
                <div style={{ fontSize: 34, fontWeight: 800, marginTop: 8 }}>{openTasks.length}</div>
                <div style={{ ...styles.muted, fontSize: 13 }}>Tasks still needing work</div>
              </div>

              <div style={styles.card}>
                <div style={styles.muted}>Today’s blocks</div>
                <div style={{ fontSize: 34, fontWeight: 800, marginTop: 8 }}>{todayBlocksCount}</div>
                <div style={{ ...styles.muted, fontSize: 13 }}>
                  {todayPlannedBlocksCount} planned, {todayDoneBlocksCount} done
                </div>
              </div>

              <div style={styles.card}>
                <div style={styles.muted}>Fixed events</div>
                <div style={{ fontSize: 34, fontWeight: 800, marginTop: 8 }}>{todayEventsCount}</div>
                <div style={{ ...styles.muted, fontSize: 13 }}>Lectures, labs, meetings</div>
              </div>

              <div style={styles.card}>
                <div style={styles.muted}>Plan status</div>
                <div style={{ fontSize: 24, fontWeight: 800, marginTop: 12 }}>
                  {dayData?.hasPlan ? "Ready" : "Not ready"}
                </div>
                <div style={{ ...styles.muted, fontSize: 13 }}>
                  {dayData?.hasPlan ? "A plan exists for this week" : "Generate a weekly plan"}
                </div>
              </div>
            </div>

            <div style={styles.twoCol}>
              <section style={styles.card}>
                <div style={styles.sectionHeader}>
                  <h3 style={styles.h3}>Today timeline</h3>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <button
                      onClick={() => generatePlan(currentWeekStart)}
                      style={{ ...styles.button, ...styles.primaryButton }}
                    >
                      Generate week
                    </button>
                    <button onClick={() => replanWeek(currentWeekStart)} style={styles.button}>
                      Replan
                    </button>
                  </div>
                </div>

                <Timeline />
              </section>

              <aside style={{ display: "grid", gap: 16 }}>
                <section style={styles.card}>
                  <h3 style={styles.h3}>Next up</h3>

                  {nextTimelineItem ? (
                    <>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                        <Badge
                          label={nextTimelineItem.type === "event" ? "EVENT" : nextTimelineItem.extra}
                          color={
                            nextTimelineItem.type === "event"
                              ? { bg: "#1e293b", border: "#334155", text: "#bfdbfe" }
                              : statusColor(nextTimelineItem.extra)
                          }
                        />
                        <b>{nextTimelineItem.title}</b>
                      </div>
                      <p style={{ ...styles.muted, marginBottom: 0 }}>
                        {formatNiceTime(nextTimelineItem.start)} → {formatNiceTime(nextTimelineItem.end)}
                        {nextTimelineItem.extra && nextTimelineItem.type === "event"
                          ? ` | ${nextTimelineItem.extra}`
                          : ""}
                      </p>
                    </>
                  ) : (
                    <p style={styles.muted}>No upcoming items left for this selected day.</p>
                  )}
                </section>

                <section style={styles.card}>
                  <h3 style={styles.h3}>Quick actions</h3>

                  <div style={{ display: "grid", gap: 8 }}>
                    <button onClick={() => setTab("tasks")} style={styles.button}>
                      Add or review tasks
                    </button>
                    <button onClick={() => setTab("planner")} style={styles.button}>
                      Open planner
                    </button>
                    <button onClick={() => replanWeek(currentWeekStart)} style={styles.button}>
                      Replan this week
                    </button>
                  </div>
                </section>

                <section style={styles.card}>
                  <h3 style={styles.h3}>Study settings</h3>
                  {prefs ? (
                    <p style={{ ...styles.muted, marginBottom: 0 }}>
                      Window:{" "}
                      <b style={{ color: "#f4f4f5" }}>
                        {prefs.dayStartHour}:00–{prefs.dayEndHour}:00
                      </b>
                      <br />
                      Block size: <b style={{ color: "#f4f4f5" }}>{prefs.blockMinutes} minutes</b>
                    </p>
                  ) : (
                    <p style={styles.muted}>No preferences loaded yet.</p>
                  )}
                </section>
              </aside>
            </div>
          </>
        )}

        {/* Tasks screen: lets the user add tasks and review open/completed tasks. */}
        {tab === "tasks" && (
          <>
            <div style={styles.sectionHeader}>
              <div>
                <h2 style={styles.h2}>Tasks</h2>
                <p style={{ ...styles.muted, margin: "6px 0 0" }}>
                  Add work items with estimates, priority, and optional deadlines.
                </p>
              </div>
            </div>

            <div style={styles.twoCol}>
              <section style={styles.card}>
                <h3 style={styles.h3}>Add new task</h3>

                <div style={styles.formGrid}>
                  <label style={styles.label}>
                    Task title
                    <input
                      value={taskTitle}
                      onChange={(e) => setTaskTitle(e.target.value)}
                      placeholder="e.g., Write final report"
                      style={styles.input}
                    />
                  </label>

                  <label style={styles.label}>
                    Deadline optional
                    <input
                      type="datetime-local"
                      value={taskDeadlineLocal}
                      onChange={(e) => setTaskDeadlineLocal(e.target.value)}
                      style={styles.input}
                    />
                  </label>

                  <label style={styles.label}>
                    Estimate minutes
                    <input
                      type="number"
                      min={1}
                      value={taskEst}
                      onChange={(e) => setTaskEst(parseInt(e.target.value || "60", 10))}
                      style={styles.input}
                    />
                  </label>

                  <label style={styles.label}>
                    Priority 0–5
                    <input
                      type="number"
                      min={0}
                      max={5}
                      value={taskPriority}
                      onChange={(e) => setTaskPriority(parseInt(e.target.value || "1", 10))}
                      style={styles.input}
                    />
                  </label>

                  <button onClick={addTask} style={{ ...styles.button, ...styles.primaryButton }}>
                    Add task
                  </button>
                </div>
              </section>

              <section style={styles.card}>
                <h3 style={styles.h3}>Open tasks</h3>

                {openTasks.length === 0 ? (
                  <p style={styles.muted}>No open tasks yet. Add a task to generate a study plan.</p>
                ) : (
                  <div style={{ display: "grid", gap: 10 }}>
                    {openTasks.map((t) => {
                      const color = priorityColor(t.priority);

                      return (
                        <div key={t.id} style={styles.cardSoft}>
                          <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
                            <b>{t.title}</b>
                            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                              <Badge label={priorityLabel(t.priority)} color={color} />
                              <Badge label={t.status} color={statusColor(t.status)} />
                            </div>
                          </div>

                          <p style={{ ...styles.muted, margin: "8px 0 0", fontSize: 13 }}>
                            Estimate: {t.estMinutes} minutes
                            {t.deadline ? ` | Due ${formatNiceDateTime(t.deadline)}` : " | No deadline"}
                          </p>

                          <button onClick={() => markTaskDone(t.id)} style={{ ...styles.button, marginTop: 10 }}>
                            Mark task done
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}

                {doneTasks.length > 0 && (
                  <>
                    <h3 style={{ ...styles.h3, marginTop: 20 }}>Completed tasks</h3>
                    <div style={{ display: "grid", gap: 8 }}>
                      {doneTasks.map((t) => (
                        <div key={t.id} style={styles.cardSoft}>
                          <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
                            <b>{t.title}</b>
                            <Badge label="DONE" color={statusColor("DONE")} />
                          </div>
                          <p style={{ ...styles.muted, margin: "6px 0 0", fontSize: 13 }}>
                            Estimate: {t.estMinutes} minutes
                            {t.deadline ? ` | Due ${formatNiceDateTime(t.deadline)}` : " | No deadline"}
                          </p>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </section>
            </div>
          </>
        )}

        {/* Planner screen: handles .ics import, plan generation, replanning and export. */}
        {tab === "planner" && (
          <>
            <div style={styles.sectionHeader}>
              <div>
                <h2 style={styles.h2}>Planner</h2>
                <p style={{ ...styles.muted, margin: "6px 0 0" }}>
                  Import your timetable, generate a weekly plan, replan changes, and export to calendar.
                </p>
              </div>
            </div>

            <div style={styles.twoCol}>
              <section style={{ display: "grid", gap: 16 }}>
                <div style={styles.card}>
                  <h3 style={styles.h3}>1. Import timetable</h3>
                  <p style={{ ...styles.muted, fontSize: 14 }}>
                    Upload an .ics calendar file. Imported events become fixed busy time that the planner avoids.
                  </p>

                  <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                    <input
                      type="file"
                      accept=".ics,text/calendar"
                      onChange={(e) => setIcsFile(e.target.files?.[0] ?? null)}
                    />
                    <button onClick={importIcs} style={styles.button}>
                      Import .ics
                    </button>
                  </div>

                  {icsFile && <p style={{ ...styles.muted, fontSize: 13 }}>Selected file: {icsFile.name}</p>}
                </div>

                <div style={styles.card}>
                  <h3 style={styles.h3}>2. Generate or replan week</h3>

                  <div style={{ display: "grid", gap: 12, maxWidth: 420 }}>
                    <label style={styles.label}>
                      Week start Monday
                      <input
                        type="date"
                        value={weekStart}
                        onChange={(e) => setWeekStart(e.target.value)}
                        style={styles.input}
                      />
                    </label>

                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <button
                        onClick={() => generatePlan(weekStart)}
                        style={{ ...styles.button, ...styles.primaryButton }}
                      >
                        Generate plan
                      </button>

                      <button onClick={() => replanWeek(weekStart)} style={styles.button}>
                        Replan week
                      </button>

                      <a href={exportUrl} style={{ ...styles.button, textDecoration: "none", display: "inline-block" }}>
                        Export .ics
                      </a>
                    </div>
                  </div>

                  {planStats && (
                    <div style={{ ...styles.cardSoft, marginTop: 14 }}>
                      <b>Plan summary</b>

                      <p style={{ ...styles.muted, marginBottom: planStats.unscheduled.length > 0 ? 10 : 0 }}>
                        Blocks scheduled:{" "}
                        <b style={{ color: "#f4f4f5" }}>{planStats.scheduledBlocks}</b> | Unscheduled tasks:{" "}
                        <b style={{ color: "#f4f4f5" }}>{planStats.unscheduledTasks}</b>
                      </p>

                      {planStats.unscheduled.length > 0 && (
                        <div
                          style={{
                            border: "1px solid #7c2d12",
                            background: "rgba(124, 45, 18, 0.18)",
                            borderRadius: 12,
                            padding: 12,
                            marginTop: 10,
                          }}
                        >
                          <b style={{ color: "#fed7aa" }}>Could not fully schedule:</b>

                          <ul style={{ marginBottom: 0 }}>
                            {planStats.unscheduled.map((task) => (
                              <li key={task.taskId} style={{ color: "#fed7aa", marginTop: 6 }}>
                                {task.title}: {task.remainingMinutes} minutes remaining
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                <div style={styles.card}>
                  <h3 style={styles.h3}>Weekly plan blocks</h3>

                  {planBlocks.length === 0 ? (
                    <p style={styles.muted}>No plan loaded here yet. Generate a weekly plan to see blocks.</p>
                  ) : (
                    <div style={{ display: "grid", gap: 10 }}>
                      {planBlocks.slice(0, 200).map((b) => (
                        <div key={b.id} style={styles.cardSoft}>
                          <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
                            <b>{b.title}</b>
                            <Badge label={b.status} color={statusColor(b.status)} />
                          </div>
                          <p style={{ ...styles.muted, margin: "8px 0 0", fontSize: 13 }}>
                            {formatNiceRange(b.startTime, b.endTime)}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}

                  {planBlocks.length > 200 && <p style={styles.muted}>Showing first 200 blocks.</p>}
                </div>
              </section>

              <aside style={{ display: "grid", gap: 16 }}>
                <section style={styles.card}>
                  <h3 style={styles.h3}>Imported events</h3>

                  {events.length === 0 ? (
                    <p style={styles.muted}>No events imported yet.</p>
                  ) : (
                    <div style={{ display: "grid", gap: 10 }}>
                      {events.slice(0, 12).map((ev) => (
                        <div key={ev.id} style={styles.cardSoft}>
                          <b>{ev.title}</b>
                          <p style={{ ...styles.muted, margin: "8px 0 0", fontSize: 13 }}>
                            {formatNiceRange(ev.startTime, ev.endTime)}
                            {ev.location ? ` | ${ev.location}` : ""}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}

                  {events.length > 12 && <p style={styles.muted}>Showing first 12 events.</p>}
                </section>

                <section style={styles.card}>
                  <h3 style={styles.h3}>Planner settings</h3>
                  {prefs ? (
                    <p style={{ ...styles.muted, marginBottom: 0 }}>
                      Study window:{" "}
                      <b style={{ color: "#f4f4f5" }}>
                        {prefs.dayStartHour}:00–{prefs.dayEndHour}:00
                      </b>
                      <br />
                      Block size: <b style={{ color: "#f4f4f5" }}>{prefs.blockMinutes} minutes</b>
                    </p>
                  ) : (
                    <p style={styles.muted}>No settings loaded.</p>
                  )}
                </section>
              </aside>
            </div>
          </>
        )}

        {/* Settings screen: controls the study window and block size used by PlanService. */}
        {tab === "settings" && (
          <>
            <div style={styles.sectionHeader}>
              <div>
                <h2 style={styles.h2}>Settings</h2>
                <p style={{ ...styles.muted, margin: "6px 0 0" }}>
                  Control the planning window and study block size.
                </p>
              </div>
            </div>

            <div style={styles.twoCol}>
              <section style={styles.card}>
                <h3 style={styles.h3}>Planning preferences</h3>

                <div style={{ ...styles.formGrid, maxWidth: 460 }}>
                  <label style={styles.label}>
                    Day start hour 0–23
                    <input
                      type="number"
                      min={0}
                      max={23}
                      value={prefDayStartHour}
                      onChange={(e) => setPrefDayStartHour(parseInt(e.target.value || "0", 10))}
                      style={styles.input}
                    />
                  </label>

                  <label style={styles.label}>
                    Day end hour 0–23
                    <input
                      type="number"
                      min={0}
                      max={23}
                      value={prefDayEndHour}
                      onChange={(e) => setPrefDayEndHour(parseInt(e.target.value || "0", 10))}
                      style={styles.input}
                    />
                  </label>

                  <label style={styles.label}>
                    Block minutes 15–240
                    <input
                      type="number"
                      min={15}
                      max={240}
                      value={prefBlockMinutes}
                      onChange={(e) => setPrefBlockMinutes(parseInt(e.target.value || "15", 10))}
                      style={styles.input}
                    />
                  </label>

                  <button onClick={updatePrefs} style={{ ...styles.button, ...styles.primaryButton }}>
                    Save settings
                  </button>
                </div>
              </section>

              <aside style={{ display: "grid", gap: 16 }}>
                <section style={styles.card}>
                  <h3 style={styles.h3}>Current settings</h3>

                  {prefs ? (
                    <p style={{ ...styles.muted, marginBottom: 0 }}>
                      Window:{" "}
                      <b style={{ color: "#f4f4f5" }}>
                        {prefs.dayStartHour}:00–{prefs.dayEndHour}:00
                      </b>
                      <br />
                      Block: <b style={{ color: "#f4f4f5" }}>{prefs.blockMinutes} minutes</b>
                      <br />
                      Updated: <b style={{ color: "#f4f4f5" }}>{formatNiceDateTime(prefs.updatedAt)}</b>
                    </p>
                  ) : (
                    <p style={styles.muted}>No preferences loaded yet.</p>
                  )}
                </section>

                <section style={styles.card}>
                  <h3 style={styles.h3}>System</h3>
                  <p style={{ ...styles.muted, marginBottom: 0 }}>
                    API connection:{" "}
                    <b style={{ color: status === "ok" ? "#bbf7d0" : "#fecaca" }}>{status}</b>
                  </p>
                </section>
              </aside>
            </div>
          </>
        )}
      </main>
    </div>
  );
}