import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS, chatNames, displayName, sourcesOf } from "@/lib/chats";
import { clearedAt, laterOf } from "@/lib/cleared";
import { filterOf, seenOf } from "@/lib/chatState";
import { matches } from "@/lib/filters";
import { loadMessages } from "@/lib/merged";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * Each chat's `unread` counts others' messages after the newest one the owner
 * has seen on any device (`lib/chatState.ts`); a chat never opened counts 0, so
 * a fresh install isn't buried in history.
 * A cleared chat's last line and unread count start after its cutoff.
 * A chat open on a filter has a last line and unread count of matching messages
 * only, so a chat on Wanted is not announced by a "good morning".
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  try {
    const chats = await Promise.all(
      CHATS.map(async (def) => {
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
    chats.sort((a, b) => (b.last?.ts ?? "").localeCompare(a.last?.ts ?? ""));
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
