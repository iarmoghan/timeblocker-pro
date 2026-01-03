
export type DateInput = string | number | Date | null | undefined;

function toDate(value: DateInput): Date | null {
  if (value === null || value === undefined) return null;

  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }

  // Date-only strings like "2025-12-13" can be interpreted as UTC midnight
  // and display as the *previous* day in some timezones. Force local midday.
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const d = new Date(`${value}T12:00:00`);
    return isNaN(d.getTime()) ? null : d;
  }

  const d = new Date(value);
  return isNaN(d.getTime()) ? null : d;
}

export function formatNiceDateTime(value: DateInput, locale = "en-US"): string {
  const d = toDate(value);
  if (!d) return "-";

  const fmt = new Intl.DateTimeFormat(locale, {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });

  const parts = fmt.formatToParts(d);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";

  const weekday = get("weekday");
  const month = get("month");
  const day = get("day");
  const year = get("year");
  const hour = get("hour");
  const minute = get("minute");
  const dayPeriod = get("dayPeriod").toLowerCase();

  const time = minute === "00" ? `${hour} ${dayPeriod}` : `${hour}:${minute} ${dayPeriod}`;
  return `${weekday}, ${month} ${day}, ${year} ${time}`;
}


export function formatNiceDate(value: DateInput, locale = "en-US"): string {
  const d = toDate(value);
  if (!d) return "-";

  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(d);
}


export function formatNiceTime(value: DateInput, locale = "en-US"): string {
  const d = toDate(value);
  if (!d) return "-";

  const fmt = new Intl.DateTimeFormat(locale, {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });

  const parts = fmt.formatToParts(d);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";

  const hour = get("hour");
  const minute = get("minute");
  const dayPeriod = get("dayPeriod").toLowerCase();

  return minute === "00" ? `${hour} ${dayPeriod}` : `${hour}:${minute} ${dayPeriod}`;
}

/**
 * A nicer "start → end" formatter.
 * If start/end are on the same day, shows:
 *   "Saturday, December 13, 2025 9 am → 10 am"
 * Otherwise falls back to full date-times for both.
 */
export function formatNiceRange(start: DateInput, end: DateInput, locale = "en-US"): string {
  const s = toDate(start);
  const e = toDate(end);
  if (!s || !e) return "-";

  const sameDay =
    s.getFullYear() === e.getFullYear() &&
    s.getMonth() === e.getMonth() &&
    s.getDate() === e.getDate();

  if (sameDay) {
    return `${formatNiceDateTime(s, locale)} → ${formatNiceTime(e, locale)}`;
  }

  return `${formatNiceDateTime(s, locale)} → ${formatNiceDateTime(e, locale)}`;
}
