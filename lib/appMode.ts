import "server-only";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";

/**
 * Which of the app's two modes the owner is in: "groups" (the groups the app was
 * built for — merged snapling, Wanted posts) or "private" (his private chats with
 * the reply helper). Kept on the server, like each chat's filter, so the phone
 * and the desktop both open where he left off. Never set = "groups", the app as
 * it was before private chats existed.
 */
export type Mode = "groups" | "private";
export const MODES: Mode[] = ["groups", "private"];

const FILE = path.join(process.cwd(), "data", "appMode.json");

export function readMode(): Mode {
  try {
    const m = JSON.parse(readFileSync(FILE, "utf8")).mode;
    return MODES.includes(m) ? m : "groups";
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return "groups";
    throw e;
  }
}

export function writeMode(mode: Mode) {
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify({ mode }, null, 2));
  renameSync(FILE + ".tmp", FILE);
}
