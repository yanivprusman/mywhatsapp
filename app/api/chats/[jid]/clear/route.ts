import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { findChat, sourcesOf } from "@/lib/chats";
import { restoreHistory, setCleared } from "@/lib/cleared";

/**
 * POST — clear the chat up to `upTo` (the newest message the phone shows; a
 * message that arrived after it stays). Without `upTo`, up to the newest stored.
 * DELETE — bring the history back. Neither touches WhatsApp: the cutoff only
 * decides what this app shows.
 */
export async function POST(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });
  const { upTo } = await req.json().catch(() => ({ upTo: undefined }));
  if (upTo != null && (typeof upTo !== "string" || Number.isNaN(Date.parse(upTo)))) {
    return Response.json({ error: "upTo must be an ISO timestamp" }, { status: 400 });
  }
  try {
    let ts: string | undefined = upTo ?? undefined;
    if (!ts) {
      const sources = sourcesOf(def);
      const [row] = await query(`SELECT MAX(timestamp) FROM messages WHERE chat_jid IN (${sources.map(() => "?").join(",")})`, sources);
      if (!row?.[0]) return Response.json({ error: "this chat has no messages to clear" }, { status: 409 });
      ts = String(row[0]).replace(" ", "T");
    }
    setCleared(def.id, ts);
    return Response.json({ clearedAt: ts });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}

export async function DELETE(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });
  try {
    restoreHistory(def.id);
    return Response.json({ clearedAt: null });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
