import "server-only";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";

/**
 * Which of the app's two sides the owner is in:
 * - "consolidated": the consolidator — the hand-picked groups (`CHATS`), merged,
 *   filtered, with Wanted alerts;
 * - "chats": every private chat and group of the account, each with the reply helper.
 * Kept on the server, like each chat's filter, so the phone and the desktop both
 * open where he left off. Never set = "consolidated", the app as it began.
 * Before 2026-11-09 the sides were "groups" / "private"; those still read.
 */
export type Mode = "consolidated" | "chats";
export const MODES: Mode[] = ["consolidated", "chats"];
const OLD: Record<string, Mode> = { groups: "consolidated", private: "chats" };

const FILE = path.join(process.cwd(), "data", "appMode.json");

export function readMode(): Mode {
  try {
    const m = JSON.parse(readFileSync(FILE, "utf8")).mode;
    return MODES.includes(m) ? m : OLD[m] ?? "consolidated";
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return "consolidated";
    throw e;
  }
}

export function writeMode(mode: Mode) {
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify({ mode }, null, 2));
  renameSync(FILE + ".tmp", FILE);
}
