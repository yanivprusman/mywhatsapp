import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS, chatNames, displayName, sourcesOf } from "@/lib/chats";
import { clearedAt, laterOf } from "@/lib/cleared";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * `seen=<json {chatId: ts}>` — the newest message the phone has shown per chat.
 * Each chat's `unread` counts others' messages after that; a chat the phone has
 * never opened counts 0, so a fresh install isn't buried in history.
 * A cleared chat's last line and unread count start after its cutoff.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  let seen: Record<string, string> = {};
  try {
    seen = JSON.parse(new URL(req.url).searchParams.get("seen") ?? "{}");
  } catch {
    return Response.json({ error: "seen must be a JSON object of chat id → timestamp" }, { status: 400 });
  }
  try {
    const chats = await Promise.all(
      CHATS.map(async (def) => {
        const sources = sourcesOf(def);
        const cutoff = clearedAt(def.id);
        const inList = `chat_jid IN (${sources.map(() => "?").join(",")})` + (cutoff ? " AND timestamp > ?" : "");
        const listArgs = cutoff ? [...sources, toDbTime(cutoff)] : sources;
        const [last] = await query(
          `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE ${inList} ORDER BY timestamp DESC LIMIT 1`,
          listArgs,
        );
        const since = laterOf(seen[def.id], cutoff);
        const [unread] = since
          ? await query(`SELECT COUNT(*) FROM messages WHERE ${inList} AND timestamp > ? AND NOT is_from_me`, [...listArgs, toDbTime(since)])
          : [[0]];
        const names = def.kind === "merged" ? await chatNames(sources) : null;
        return {
          jid: def.id,
          name: await displayName(def),
          isGroup: def.kind === "merged" || def.id.endsWith("@g.us"),
          merged: def.kind === "merged",
          canSend: def.kind === "chat" && def.canSend,
          sources: names ? sources.map((j) => ({ jid: j, name: names.get(j) ?? j })) : [],
          clearedAt: cutoff,
          unread: Number(unread?.[0] ?? 0),
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
