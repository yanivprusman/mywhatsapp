import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS, chatNames, displayName, sourcesOf } from "@/lib/chats";
import { isWanted } from "@/lib/filters";
import { normalizeText } from "@/lib/merged";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

const REPOST_WINDOW_DAYS = 30;

/**
 * New Wanted posts across every chat the app shows — what the phone's watcher
 * polls to raise a notification per job post.
 *
 * `after=<ts>` returns the posts since then (oldest first, so notifications
 * fire in order). Without `after` it returns no posts, only `latest`: a fresh
 * install starts from now instead of alerting on the whole history.
 * In a merged chat, a person re-posting the same text (in any of its groups,
 * within 30 days) does not alert again.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const after = new URL(req.url).searchParams.get("after");
  const every = CHATS.flatMap(sourcesOf);
  try {
    if (!after) {
      const [row] = await query(`SELECT MAX(timestamp) FROM messages WHERE chat_jid IN (${every.map(() => "?").join(",")})`, every);
      return Response.json({ posts: [], latest: row?.[0] ? String(row[0]).replace(" ", "T") : new Date().toISOString() });
    }
    const posts = [];
    let latest = after;
    for (const def of CHATS) {
      const sources = sourcesOf(def);
      const inList = `chat_jid IN (${sources.map(() => "?").join(",")})`;
      const rows = await query(
        `SELECT ${MESSAGE_COLUMNS}, chat_jid FROM messages WHERE ${inList} AND timestamp > ? ORDER BY timestamp ASC LIMIT 200`,
        [...sources, toDbTime(after)],
      );
      const fresh = rows.map((r) => ({ ...toMessage(r), chatJid: String(r[8]) }));
      for (const m of fresh) if (m.ts > latest) latest = m.ts;
      const candidates = fresh.filter((m) => !m.fromMe && isWanted(m));
      const firsts: typeof candidates = [];
      for (const m of candidates) {
        if (def.kind === "merged" && /^\d{6,15}$/.test(m.sender)) {
          const earlier = await query(
            `SELECT content FROM messages WHERE ${inList} AND sender = ? AND timestamp < ? AND timestamp > datetime(?, '-${REPOST_WINDOW_DAYS} days')`,
            [...sources, m.sender, toDbTime(m.ts), toDbTime(m.ts)],
          );
          const text = normalizeText(m.text);
          if (earlier.some(([c]) => normalizeText(String(c ?? "")) === text)) continue;
          if (firsts.some((f) => f.sender === m.sender && normalizeText(f.text) === text)) continue;
        }
        firsts.push(m);
      }
      const name = await displayName(def);
      const groups = def.kind === "merged" ? await chatNames(sources) : null;
      const named = await withSenderNames(firsts);
      // chatName on the message = the real group it was posted in (what a private reply should name).
      posts.push(...named.map((m, i) => ({
        chatJid: def.id, chatName: name,
        message: { ...m, chatName: groups?.get(firsts[i].chatJid) },
      })));
    }
    posts.sort((a, b) => a.message.ts.localeCompare(b.message.ts));
    return Response.json({ posts, latest });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
