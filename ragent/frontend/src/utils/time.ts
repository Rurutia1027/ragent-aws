import { format, differenceInMinutes, differenceInHours, differenceInDays } from "date-fns";

const WEEKDAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const getWeekStart = (d: Date) => {
  const date = new Date(d);
  const day = date.getDay();
  const diff = day === 0 ? 6 : day - 1;
  date.setDate(date.getDate() - diff);
  date.setHours(0, 0, 0, 0);
  return date;
};

const formatRelativeNumeric = (value: string) => {
  const date = new Date(value);
  const diffMinutes = differenceInMinutes(new Date(), date);
  const diffHours = differenceInHours(new Date(), date);
  const diffDays = differenceInDays(new Date(), date);
  if (diffMinutes < 1) return "Just now";
  if (diffMinutes < 60) return `${diffMinutes} minutes ago`;
  if (diffHours < 24) return `${diffHours} hours ago`;
  return `${diffDays} days ago`;
};

export const formatRelativeTime = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const diffMinutes = differenceInMinutes(now, date);
  const diffHours = differenceInHours(now, date);
  if (diffMinutes < 1) return "Just now";
  if (diffMinutes < 60) return `${diffMinutes} minutes ago`;
  if (date.getDate() === now.getDate() && date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear()) {
    return `${diffHours} hours ago`;
  }
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (date.getDate() === yesterday.getDate() && date.getMonth() === yesterday.getMonth() && date.getFullYear() === yesterday.getFullYear()) {
    return `Yesterday ${format(date, "HH:mm")}`;
  }
  const weekStart = getWeekStart(now);
  if (date >= weekStart) {
    return `${WEEKDAY_NAMES[date.getDay()]} ${format(date, "HH:mm")}`;
  }
  if (date.getFullYear() === now.getFullYear()) {
    return format(date, "MMM d");
  }
  return format(date, "MMM d, yyyy");
};

export const formatFullDateTime = (value?: string | null) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return format(date, "yyyy-MM-dd HH:mm:ss");
};

export const formatTooltipTime = (value?: string | null) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${format(date, "yyyy-MM-dd HH:mm:ss")} · ${formatRelativeNumeric(value)}`;
};
