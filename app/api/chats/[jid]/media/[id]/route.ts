import { authorize } from "@/lib/auth";
import { media } from "@/lib/bridge";
import { allowed } from "@/lib/chats";

export async function GET(req: Request, { params }: { params: Promise<{ jid: string; id: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid, id } = await params;
  if (!allowed(jid)) return Response.json({ error: "unknown chat" }, { status: 404 });
  try {
    const { bytes, type } = await media(jid, id);
    return new Response(bytes, { headers: { "Content-Type": type, "Cache-Control": "private, max-age=31536000, immutable" } });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
