import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { chatNames, displayName, sourcesOf, visibleChats } from "@/lib/chats";
import { clearedAt, laterOf } from "@/lib/cleared";
import { filterOf, seenOf } from "@/lib/chatState";
import { matches } from "@/lib/filters";
import { directNames, recentDirects } from "@/lib/direct";
import { loadMessages } from "@/lib/merged";
import { readVisibility } from "@/lib/visibility";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * Each chat's `unread` counts others' messages after the newest one the owner
 * has seen on any device (`lib/chatState.ts`); a chat never opened counts 0, so
 * a fresh install isn't buried in history.
 * A cleared chat's last line and unread count start after its cutoff.
 * A chat open on a filter has a last line and unread count of matching messages
 * only, so a chat on Wanted is not announced by a "good morning".
 * Private chats follow the groups: the DIRECT_LIMIT most recent ones (all of them
 * are reachable; the list is just as long as is worth scrolling), read-only.
 */
const DIRECT_LIMIT = 80;
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  try {
    const chats = await Promise.all(
      visibleChats().map(async (def) => {
        const sources = sourcesOf(def);
        const cutoff = clearedAt(def.id);
        const inList = `chat_jid IN (${sources.map(() => "?").join(",")})` + (cutoff ? " AND timestamp > ?" : "");
        const listArgs = cutoff ? [...sources, toDbTime(cutoff)] : sources;
        const since = laterOf(seenOf(def.id), cutoff);
        const filter = filterOf(def.id);
        let last: ReturnType<typeof toMessage> | undefined;
        let unread: number;
        if (filter === "all") {
          const [row] = await query(
            `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE ${inList} ORDER BY timestamp DESC LIMIT 1`,
            listArgs,
          );
          last = row ? toMessage(row) : undefined;
          const [count] = since
            ? await query(`SELECT COUNT(*) FROM messages WHERE ${inList} AND timestamp > ? AND NOT is_from_me`, [...listArgs, toDbTime(since)])
            : [[0]];
          unread = Number(count?.[0] ?? 0);
        } else {
          // Filters classify text, which SQL can't: the same messages the chat shows (reposts collapsed), newest first.
          const hits = (await loadMessages(def, cutoff ? { after: cutoff } : {})).filter((m) => matches(filter, m));
          last = hits[0];
          unread = since ? hits.filter((m) => m.ts > since && !m.fromMe).length : 0;
        }
        const names = def.kind === "merged" ? await chatNames(sources) : null;
        return {
          jid: def.id,
          name: await displayName(def),
          isGroup: def.kind === "merged" || def.id.endsWith("@g.us"),
          merged: def.kind === "merged",
          canSend: def.kind === "chat" && def.canSend,
          sources: names ? sources.map((j) => ({ jid: j, name: names.get(j) ?? j })) : [],
          clearedAt: cutoff,
          filter,
          unread,
          last: last ? (await withSenderNames([last]))[0] : null,
        };
      }),
    );
    chats.push(...(await directChats()));
    chats.sort((a, b) => (b.last?.ts ?? "").localeCompare(a.last?.ts ?? ""));
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}

/** The recent private chats as list rows: one query for the last lines, one for unread. */
async function directChats() {
  const hidden = new Set(readVisibility().hiddenChats);
  const recent = (await recentDirects(DIRECT_LIMIT)).filter((d) => !hidden.has(d.jid));
  if (!recent.length) return [];
  const names = await directNames(recent.map((d) => d.jid));
  const since = new Map(recent.flatMap((d) => {
    const s = laterOf(seenOf(d.jid), clearedAt(d.jid));
    return s ? [[d.jid, s] as const] : [];
  }));
  const unread = new Map<string, number>();
  if (since.size) {
    const jids = [...since.keys()];
    const oldest = [...since.values()].reduce((a, b) => (Date.parse(a) < Date.parse(b) ? a : b));
    const rows = await query(
      `SELECT chat_jid, timestamp FROM messages WHERE chat_jid IN (${jids.map(() => "?").join(",")}) AND NOT is_from_me AND timestamp > ?`,
      [...jids, toDbTime(oldest)],
    );
    for (const [jid, ts] of rows) {
      if (Date.parse(String(ts).replace(" ", "T")) > Date.parse(since.get(String(jid))!)) unread.set(String(jid), (unread.get(String(jid)) ?? 0) + 1);
    }
  }
  return recent.map(({ jid, last }) => ({
    jid,
    name: names.get(jid)!,
    isGroup: false,
    merged: false,
    canSend: false,
    sources: [] as { jid: string; name: string }[],
    clearedAt: clearedAt(jid),
    filter: "all" as const,
    unread: unread.get(jid) ?? 0,
    last: { ...last, senderName: last.fromMe ? "You" : names.get(jid) },
  }));
}
