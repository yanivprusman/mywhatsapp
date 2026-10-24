import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS } from "@/lib/chats";
import { MESSAGE_COLUMNS, toMessage, withSenderNames } from "@/lib/messages";

export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  try {
    const chats = await Promise.all(
      CHATS.map(async ({ jid }) => {
        const [info] = await query("SELECT jid, name FROM chats WHERE jid = ?", [jid]);
        const [last] = await query(
          `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE chat_jid = ? ORDER BY timestamp DESC LIMIT 1`,
          [jid],
        );
        return {
          jid,
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
