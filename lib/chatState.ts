import "server-only";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { Filter } from "@/lib/filters";

/**
 * What the owner last did in each chat, shared by every device he reads it on:
 * the filter it is open on, and the newest message he has seen (unread badges
 * count after it). Kept on the server, not per device, so choosing Wanted on
 * the phone opens the chat on Wanted on the desktop too, and reading a chat on
 * one clears its badge on the other.
 */
const FILE = path.join(process.cwd(), "data", "chatState.json");

type State = { filters: Record<string, Filter>; seen: Record<string, string> };

function readAll(): State {
  try {
    const s = JSON.parse(readFileSync(FILE, "utf8"));
    return { filters: s.filters ?? {}, seen: s.seen ?? {} };
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return { filters: {}, seen: {} };
    throw e;
  }
}

function writeAll(s: State) {
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify(s, null, 2));
  renameSync(FILE + ".tmp", FILE);
}

export function filterOf(chatId: string): Filter {
  return readAll().filters[chatId] ?? "all";
}

export function setFilter(chatId: string, filter: Filter) {
  const s = readAll();
  const { [chatId]: _, ...rest } = s.filters;
  writeAll({ ...s, filters: filter === "all" ? rest : { ...rest, [chatId]: filter } });
}

export function seenOf(chatId: string): string | null {
  return readAll().seen[chatId] ?? null;
}

/** Moves forward only: a device showing an older page can't bring read messages back as unread. */
export function markSeen(chatId: string, ts: string) {
  const s = readAll();
  const cur = s.seen[chatId];
  // Compared as instants: the bridge's offsets change at DST (+03:00 → +02:00), so the strings don't sort.
  if (cur && Date.parse(cur) >= Date.parse(ts)) return;
  writeAll({ ...s, seen: { ...s.seen, [chatId]: ts } });
}
