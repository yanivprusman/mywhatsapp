import "server-only";
import { query } from "@/lib/bridge";
import { directNames, isDirect } from "@/lib/direct";
import { readVisibility } from "@/lib/visibility";

/**
 * The chats MyWhatsApp shows. An allowlist on purpose: the bridge holds the
 * whole account, and this app must reach only what was added here.
 *
 * - `chat`: one real WhatsApp chat. `canSend` only for the owner's own preview
 *   group — the app must never post into a group of strangers.
 * - `merged`: several real groups shown as ONE chat, reposts collapsed. Read-only;
 *   answering happens privately, from a post. Add a group by appending its jid
 *   to `sources` — the name and the "N groups" count follow.
 * - `direct`: a private chat. Every private chat is reachable (the reply helper,
 *   `lib/suggest.ts`, covers them all — `lib/direct.ts`); none is listed here.
 *   Read-only in the app: a reply is sent from real WhatsApp.
 */
export type ChatDef =
  | { kind: "chat"; id: string; canSend: boolean }
  | { kind: "merged"; id: string; base: string; sources: string[] }
  | { kind: "direct"; id: string };

export const CHATS: ChatDef[] = [
  { kind: "chat", id: "120363427371815253@g.us", canSend: true }, // קבוצה ריקה — the owner's own preview group
  { kind: "chat", id: "120363429409731384@g.us", canSend: false }, // הדפסה תלת מימד
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

/**
 * The chats as the owner chose to see them (Tools → Groups, `lib/visibility.ts`):
 * hidden chats left out, a merged chat without its hidden groups. A merged chat
 * whose every group is hidden is left out too — it would have nothing to show.
 * Everything the app shows or alerts on goes through this; `CHATS` itself is
 * only the ceiling of what may be reached.
 */
export function visibleChats(): ChatDef[] {
  const { hiddenChats, hiddenSources } = readVisibility();
  return CHATS.flatMap((def): ChatDef[] => {
    if (hiddenChats.includes(def.id)) return [];
    if (def.kind !== "merged") return [def];
    const sources = def.sources.filter((j) => !hiddenSources.includes(j));
    return sources.length ? [{ ...def, sources }] : [];
  });
}

/** A chat the owner currently sees, with only its shown groups; any private chat he hasn't hidden. Hidden = not found. */
export function findChat(id: string): ChatDef | undefined {
  if (isDirect(id)) return readVisibility().hiddenChats.includes(id) ? undefined : { kind: "direct", id };
  return visibleChats().find((c) => c.id === id);
}

export function sourcesOf(def: ChatDef): string[] {
  return def.kind === "merged" ? def.sources : [def.id];
}

/** A jid whose messages (and media) the app may read: a listed chat, a merged chat's source, or a private chat. */
export function readable(jid: string): boolean {
  return isDirect(jid) || CHATS.some((c) => c.id === jid || sourcesOf(c).includes(jid));
}

/** Real names of WhatsApp chats, from the bridge. */
export async function chatNames(jids: string[]): Promise<Map<string, string>> {
  const rows = await query(`SELECT jid, name FROM chats WHERE jid IN (${jids.map(() => "?").join(",")})`, jids);
  return new Map(rows.map(([j, n]) => [String(j), String(n ?? j)]));
}

/** The name the phone shows. A merged chat says so, and how many groups it is made of. */
export async function displayName(def: ChatDef): Promise<string> {
  if (def.kind === "merged") return `${def.base} · ${def.sources.length} קבוצות`;
  if (def.kind === "direct") return (await directNames([def.id])).get(def.id)!;
  return (await chatNames([def.id])).get(def.id) ?? def.id;
}
