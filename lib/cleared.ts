import "server-only";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";

/**
 * "Clear chat": the owner has read a chat up to a point and wants a clean slate.
 * Nothing is deleted — the messages belong to real WhatsApp groups and stay in
 * the bridge. A chat's cutoff only hides what is at or before it, so restoring
 * history is just forgetting the cutoff. Kept on the server, not the phone, so
 * counts, unread badges and the chat list's last line all agree with the chat.
 */
const FILE = path.join(process.cwd(), "data", "cleared.json");

function readAll(): Record<string, string> {
  try {
    return JSON.parse(readFileSync(FILE, "utf8"));
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return {};
    throw e;
  }
}

function writeAll(all: Record<string, string>) {
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify(all, null, 2));
  renameSync(FILE + ".tmp", FILE);
}

/** The timestamp a chat was cleared up to (inclusive), or null. */
export function clearedAt(chatId: string): string | null {
  return readAll()[chatId] ?? null;
}

export function setCleared(chatId: string, ts: string) {
  writeAll({ ...readAll(), [chatId]: ts });
}

export function restoreHistory(chatId: string) {
  const { [chatId]: _, ...rest } = readAll();
  writeAll(rest);
}

/** The later of a caller's `after` and the chat's cutoff — what a read should start after. */
export function laterOf(a: string | null | undefined, b: string | null): string | null {
  if (!a) return b;
  if (!b) return a;
  return a > b ? a : b;
}
