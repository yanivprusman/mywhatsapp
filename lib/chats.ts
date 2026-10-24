import "server-only";
import { query } from "@/lib/bridge";

/**
 * The chats MyWhatsApp shows. An allowlist on purpose: the bridge holds the
 * whole account, and this app must reach only what was added here.
 *
 * - `chat`: one real WhatsApp chat. `canSend` only for the owner's own preview
 *   group — the app must never post into a group of strangers.
 * - `merged`: several real groups shown as ONE chat, reposts collapsed. Read-only;
 *   answering happens privately, from a post. Add a group by appending its jid
 *   to `sources` — the name and the "N groups" count follow.
 */
export type ChatDef =
  | { kind: "chat"; id: string; canSend: boolean }
  | { kind: "merged"; id: string; base: string; sources: string[] };

export const CHATS: ChatDef[] = [
  { kind: "chat", id: "120363427371815253@g.us", canSend: true }, // קבוצה ריקה — the owner's own preview group
  {
    kind: "merged",
    id: "merged-snapling",
    base: "סנפלינג מאוחד",
    sources: [
      "972509974742-1431932281@g.us", // סנפלינג
      "972546682313-1454179388@g.us", // עבודות סנפלינג וגובה
      "972535301491-1564139897@g.us", // 💪סנפלינג-ואוסמוזה 💪
      "120363162523631612@g.us", // רק רשתות
    ],
  },
];

export function findChat(id: string): ChatDef | undefined {
  return CHATS.find((c) => c.id === id);
}

export function sourcesOf(def: ChatDef): string[] {
  return def.kind === "chat" ? [def.id] : def.sources;
}

/** A jid whose messages (and media) the app may read: a listed chat or a merged chat's source. */
export function readable(jid: string): boolean {
  return CHATS.some((c) => c.id === jid || sourcesOf(c).includes(jid));
}

/** Real names of WhatsApp chats, from the bridge. */
export async function chatNames(jids: string[]): Promise<Map<string, string>> {
  const rows = await query(`SELECT jid, name FROM chats WHERE jid IN (${jids.map(() => "?").join(",")})`, jids);
  return new Map(rows.map(([j, n]) => [String(j), String(n ?? j)]));
}

/** The name the phone shows. A merged chat says so, and how many groups it is made of. */
export async function displayName(def: ChatDef): Promise<string> {
  if (def.kind === "merged") return `${def.base} · ${def.sources.length} קבוצות`;
  return (await chatNames([def.id])).get(def.id) ?? def.id;
}
