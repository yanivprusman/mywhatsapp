import { authorize } from "@/lib/auth";
import { sendText } from "@/lib/bridge";
import { findChat } from "@/lib/chats";

/** Text to a chat marked `canSend` — only the owner's own preview group; never a group of strangers. */
export async function POST(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });
  if (def.kind !== "chat" || !def.canSend) return Response.json({ error: "this chat is read-only" }, { status: 403 });
  const { text } = await req.json().catch(() => ({ text: "" }));
  if (typeof text !== "string" || !text.trim()) return Response.json({ error: "empty message" }, { status: 400 });
  try {
    await sendText(def.id, text);
    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
