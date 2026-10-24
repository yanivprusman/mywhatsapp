import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS } from "@/lib/chats";
import { isWanted } from "@/lib/filters";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * New Wanted posts across every chat the app shows — what the phone's watcher
 * polls to raise a notification per job post.
 *
 * `after=<ts>` returns the posts since then (oldest first, so notifications
 * fire in order). Without `after` it returns no posts, only `latest`: a fresh
 * install starts from now instead of alerting on the whole history.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const after = new URL(req.url).searchParams.get("after");
  try {
    if (!after) {
      const [row] = await query(
        `SELECT MAX(timestamp) FROM messages WHERE chat_jid IN (${CHATS.map(() => "?").join(",")})`,
        CHATS.map((c) => c.jid),
      );
      return Response.json({ posts: [], latest: row?.[0] ? String(row[0]).replace(" ", "T") : new Date().toISOString() });
    }
    const posts = [];
    let latest = after;
    for (const { jid } of CHATS) {
      const [info] = await query("SELECT name FROM chats WHERE jid = ?", [jid]);
      const rows = await query(
        `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE chat_jid = ? AND timestamp > ? ORDER BY timestamp ASC LIMIT 200`,
        [jid, toDbTime(after)],
      );
      const all = rows.map(toMessage);
      for (const m of all) if (m.ts > latest) latest = m.ts;
      const wanted = await withSenderNames(all.filter((m) => !m.fromMe && isWanted(m)));
      posts.push(...wanted.map((message) => ({ chatJid: jid, chatName: String(info?.[0] ?? jid), message })));
    }
    posts.sort((a, b) => a.message.ts.localeCompare(b.message.ts));
    return Response.json({ posts, latest });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
