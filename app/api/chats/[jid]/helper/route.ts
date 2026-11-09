import { authorize } from "@/lib/auth";
import { findConversation } from "@/lib/chats";
import { dropThread, helperTurn, readThread } from "@/lib/suggest";

/**
 * The reply helper's conversation for one chat — any private chat or group on the Chats side — see lib/suggest.ts.
 * GET → `{turns}` (empty when none started). POST `{said?}` → one more turn, `{turns}`.
 * DELETE → start over. Only drafts: nothing is ever sent from here.
 */
async function conversation(req: Request, params: Promise<{ jid: string }>) {
  const denied = authorize(req);
  if (denied) return { error: denied };
  const { jid } = await params;
  const def = findConversation(jid);
  if (!def) return { error: Response.json({ error: "not a chat on the Chats side (or hidden there)" }, { status: 404 }) };
  return { jid: def.id };
}

export async function GET(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const r = await conversation(req, params);
  if (r.error) return r.error;
  return Response.json({ turns: readThread(r.jid)?.turns ?? [] });
}

export async function POST(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const r = await conversation(req, params);
  if (r.error) return r.error;
  const body = (await req.json().catch(() => ({}))) as { said?: unknown };
  const said = typeof body.said === "string" ? body.said.slice(0, 4000) : "";
  try {
    return Response.json({ turns: (await helperTurn(r.jid, said)).turns });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}

export async function DELETE(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const r = await conversation(req, params);
  if (r.error) return r.error;
  dropThread(r.jid);
  return Response.json({ turns: [] });
}
