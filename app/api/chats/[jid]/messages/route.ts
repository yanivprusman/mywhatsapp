import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { allowed } from "@/lib/chats";
import { MESSAGE_COLUMNS, toDbTime, toMessage } from "@/lib/messages";

/**
 * Newest first. `before=<ts>` pages back into history; `after=<ts>` returns only
 * what arrived since (the phone polls with it while a chat is open).
 */
export async function GET(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  if (!allowed(jid)) return Response.json({ error: "unknown chat" }, { status: 404 });

  const sp = new URL(req.url).searchParams;
  const limit = Math.min(Number(sp.get("limit") ?? 50) || 50, 200);
  const before = sp.get("before");
  const after = sp.get("after");
  let where = "chat_jid = ?";
  const args: unknown[] = [jid];
  if (before) { where += " AND timestamp < ?"; args.push(toDbTime(before)); }
  if (after) { where += " AND timestamp > ?"; args.push(toDbTime(after)); }
  try {
    const rows = await query(
      `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE ${where} ORDER BY timestamp DESC LIMIT ?`,
      [...args, limit],
    );
    return Response.json({ messages: rows.map(toMessage), hasMore: !after && rows.length === limit });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
