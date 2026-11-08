import "server-only";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";

/**
 * Which of the allowlisted chats the owner wants to see, switched on and off
 * from Tools → Groups. Two independent sets of off-switches:
 *
 * - `hiddenChats`: a whole chat (an id from `CHATS`) — gone from the chat list
 *   and from Wanted alerts.
 * - `hiddenSources`: one real group inside a merged chat — its posts leave the
 *   merged conversation, its count and its alerts, the other groups stay.
 *
 * Kept on the server so the phone and the desktop show the same list. This
 * narrows the allowlist in `lib/chats.ts`; it can never widen it — an id that
 * is not listed there is refused before it gets here.
 */
const FILE = path.join(process.cwd(), "data", "visibility.json");

export type Visibility = { hiddenChats: string[]; hiddenSources: string[] };

export function readVisibility(): Visibility {
  try {
    const v = JSON.parse(readFileSync(FILE, "utf8"));
    return { hiddenChats: v.hiddenChats ?? [], hiddenSources: v.hiddenSources ?? [] };
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return { hiddenChats: [], hiddenSources: [] };
    throw e;
  }
}

function toggle(list: string[], id: string, hidden: boolean): string[] {
  const rest = list.filter((x) => x !== id);
  return hidden ? [...rest, id] : rest;
}

export function setHidden(kind: "chat" | "source", id: string, hidden: boolean) {
  const v = readVisibility();
  const next: Visibility = kind === "chat"
    ? { ...v, hiddenChats: toggle(v.hiddenChats, id, hidden) }
    : { ...v, hiddenSources: toggle(v.hiddenSources, id, hidden) };
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify(next, null, 2));
  renameSync(FILE + ".tmp", FILE);
}
