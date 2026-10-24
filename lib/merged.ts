import "server-only";
import { query } from "@/lib/bridge";
import { type ChatDef, chatNames, sourcesOf } from "@/lib/chats";
import { MESSAGE_COLUMNS, type WireMessage, toDbTime, toMessage } from "@/lib/messages";

export type ChatMessage = WireMessage & {
  chatJid: string;         // the real group it came from
  chatName?: string;       // set for merged chats: which group
  repeats?: number;        // how many times this person posted this text (merged chats)
  alsoIn?: string[];       // other groups it was also posted in
};

const MARKS = /[‎‏‪-‮⁦-⁩*_~]/g;

/** Same post = same words, whatever the spacing, RTL marks or *bold* around them. */
export function normalizeText(text: string): string {
  return text.replace(MARKS, "").replace(/\s+/g, " ").trim().toLowerCase();
}

function dupKey(m: WireMessage): string | null {
  // Only a known person and real text: two photos, or two posts of an unrecorded sender, are not "the same".
  if (!/^\d{6,15}$/.test(m.sender) || !m.text.trim()) return null;
  return `${m.sender}\u0000${normalizeText(m.text)}`;
}

/**
 * Messages of a chat, newest first. For a merged chat: every source group
 * together, and a person posting the same text again (in the same group or
 * another) collapses into its NEWEST copy, which carries the count and the groups.
 */
export async function loadMessages(def: ChatDef, range: { before?: string | null; after?: string | null } = {}): Promise<ChatMessage[]> {
  const sources = sourcesOf(def);
  let where = `chat_jid IN (${sources.map(() => "?").join(",")})`;
  const args: unknown[] = [...sources];
  if (range.before) { where += " AND timestamp < ?"; args.push(toDbTime(range.before)); }
  if (range.after) { where += " AND timestamp > ?"; args.push(toDbTime(range.after)); }
  const rows = await query(`SELECT ${MESSAGE_COLUMNS}, chat_jid FROM messages WHERE ${where} ORDER BY timestamp DESC`, args);
  const all: ChatMessage[] = rows.map((r) => ({ ...toMessage(r), chatJid: String(r[8]) }));
  if (def.kind !== "merged") return all;

  const names = await chatNames(sources);
  const kept = new Map<string, ChatMessage>();
  const out: ChatMessage[] = [];
  for (const m of all) {
    const named = { ...m, chatName: names.get(m.chatJid) ?? m.chatJid };
    const key = dupKey(m);
    const first = key ? kept.get(key) : undefined;
    if (!first) {
      if (key) kept.set(key, named);
      out.push(named);
      continue;
    }
    first.repeats = (first.repeats ?? 1) + 1;
    if (named.chatName !== first.chatName && !(first.alsoIn ?? []).includes(named.chatName!)) {
      first.alsoIn = [...(first.alsoIn ?? []), named.chatName!];
    }
  }
  return out;
}
