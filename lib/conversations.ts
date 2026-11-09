import "server-only";
import { query } from "@/lib/bridge";
import { MESSAGE_COLUMNS, type WireMessage, toMessage } from "@/lib/messages";

/**
 * Conversations: every private chat AND every group of the account, as real
 * WhatsApp lists them. This is the reply helper's side of the app (`lib/suggest.ts`),
 * and it is NOT an allowlist — the helper only drafts, and only when asked, so
 * there is nothing to opt a chat into. The consolidator's hand-picked list
 * (`CHATS` in `lib/chats.ts`: merged groups, filters, Wanted alerts) is a separate
 * thing; a group can be in both, and neither depends on the other.
 *
 * A private chat is a phone jid (`<digits>@s.whatsapp.net`), a group is
 * `<id>@g.us`. `@lid` chats are left out: the bridge stores no messages under
 * them (measured 2026-11-09, 25 chats, 0 messages); so is `status@broadcast`.
 * The owner's own number is left out of the list — there is nobody there to answer.
 */
const DIRECT = /^\d{6,15}@s\.whatsapp\.net$/;
const GROUP = /^[\d-]{6,40}@g\.us$/;

export function isDirect(jid: string): boolean {
  return DIRECT.test(jid);
}

export function isGroupJid(jid: string): boolean {
  return GROUP.test(jid);
}

export function isConversation(jid: string): boolean {
  return isDirect(jid) || isGroupJid(jid);
}

/** The phone number of a private chat, as WhatsApp links want it (digits only). */
export function phoneOf(jid: string): string {
  return jid.split("@")[0];
}

let own: string | null = null;
/** The account's own phone jid, from the bridge's linked device ("972…:27@s.whatsapp.net"). */
export async function ownJid(): Promise<string> {
  if (own) return own;
  const [row] = await query("SELECT jid FROM whatsmeow_device LIMIT 1", [], "whatsmeow");
  if (!row) throw new Error("bridge has no linked device");
  own = `${String(row[0]).split(/[:@]/)[0]}@s.whatsapp.net`;
  return own;
}

/** The [limit] conversations (private and group) with the newest messages, each with that newest message. */
export async function recentConversations(limit: number): Promise<{ jid: string; last: WireMessage }[]> {
  const me = await ownJid();
  const rows = await query(
    `SELECT ${MESSAGE_COLUMNS}, m.chat_jid FROM messages m
     JOIN (SELECT chat_jid, MAX(timestamp) t FROM messages
           WHERE (chat_jid LIKE '%@s.whatsapp.net' OR chat_jid LIKE '%@g.us') AND chat_jid != ?
           GROUP BY chat_jid ORDER BY t DESC LIMIT ?) x
       ON m.chat_jid = x.chat_jid AND m.timestamp = x.t`,
    [me, limit],
  );
  const seen = new Set<string>();
  const out: { jid: string; last: WireMessage }[] = [];
  for (const r of rows) {
    const jid = String(r[8]);
    if (!isConversation(jid) || seen.has(jid)) continue; // two messages in the same second: keep one
    seen.add(jid);
    out.push({ jid, last: toMessage(r) });
  }
  return out;
}

/** What WhatsApp would call each person: saved name, else "~ their own name", else business name, else +number. */
export async function directNames(jids: string[]): Promise<Map<string, string>> {
  const names = new Map<string, string>();
  for (let i = 0; i < jids.length; i += 200) {
    const batch = jids.slice(i, i + 200);
    const rows = await query(
      `SELECT their_jid, full_name, push_name, business_name FROM whatsmeow_contacts WHERE their_jid IN (${batch.map(() => "?").join(",")})`,
      batch,
      "whatsmeow",
    );
    for (const [jid, full, push, biz] of rows) {
      const name = full ? String(full) : push ? `~ ${push}` : biz ? String(biz) : "";
      if (name) names.set(String(jid), name);
    }
  }
  for (const j of jids) if (!names.has(j)) names.set(j, `+${phoneOf(j)}`);
  return names;
}

/** Names for any mix of conversations: a person's as WhatsApp shows it, a group's from the bridge. */
export async function conversationNames(jids: string[]): Promise<Map<string, string>> {
  const people = jids.filter(isDirect);
  const groups = jids.filter(isGroupJid);
  const names = people.length ? await directNames(people) : new Map<string, string>();
  if (groups.length) {
    for (let i = 0; i < groups.length; i += 200) {
      const batch = groups.slice(i, i + 200);
      const rows = await query(`SELECT jid, name FROM chats WHERE jid IN (${batch.map(() => "?").join(",")})`, batch);
      for (const [j, n] of rows) if (n) names.set(String(j), String(n));
    }
    for (const g of groups) if (!names.has(g)) names.set(g, "Unnamed group");
  }
  return names;
}
