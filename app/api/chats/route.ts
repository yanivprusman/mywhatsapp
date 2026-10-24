import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS } from "@/lib/chats";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * `seen=<json {jid: ts}>` — the newest message the phone has shown per chat.
 * Each chat's `unread` counts others' messages after that; a chat the phone has
 * never opened counts 0, so a fresh install isn't buried in history.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  let seen: Record<string, string> = {};
  try {
    seen = JSON.parse(new URL(req.url).searchParams.get("seen") ?? "{}");
  } catch {
    return Response.json({ error: "seen must be a JSON object of jid → timestamp" }, { status: 400 });
  }
  try {
    const chats = await Promise.all(
      CHATS.map(async ({ jid }) => {
        const [info] = await query("SELECT jid, name FROM chats WHERE jid = ?", [jid]);
        const [last] = await query(
          `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE chat_jid = ? ORDER BY timestamp DESC LIMIT 1`,
          [jid],
        );
        const since = seen[jid];
        const [unread] = since
          ? await query("SELECT COUNT(*) FROM messages WHERE chat_jid = ? AND timestamp > ? AND NOT is_from_me", [jid, toDbTime(since)])
          : [[0]];
        return {
          jid,
          unread: Number(unread?.[0] ?? 0),
          name: String(info?.[1] ?? jid),
          isGroup: jid.endsWith("@g.us"),
          last: last ? (await withSenderNames([toMessage(last)]))[0] : null,
        };
      }),
    );
    chats.sort((a, b) => (b.last?.ts ?? "").localeCompare(a.last?.ts ?? ""));
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
