import { authorize } from "@/lib/auth";
import { sendText } from "@/lib/bridge";
import { allowed } from "@/lib/chats";

export async function POST(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  if (!allowed(jid)) return Response.json({ error: "unknown chat" }, { status: 404 });
  const { text } = await req.json().catch(() => ({ text: "" }));
  if (typeof text !== "string" || !text.trim()) return Response.json({ error: "empty message" }, { status: 400 });
  try {
    await sendText(jid, text);
    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
