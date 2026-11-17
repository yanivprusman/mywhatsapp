import { authorize } from "@/lib/auth";
import { readable } from "@/lib/chats";
import { startTranscript, transcriptState } from "@/lib/transcripts";

/**
 * A voice message's transcript. GET reads where it stands (done / working / failed /
 * none); POST starts it and answers at once — the phone then asks again with GET
 * until it is done, because whisperd can take minutes on a long note.
 */
async function target(req: Request, params: Promise<{ jid: string; id: string }>) {
  const denied = authorize(req);
  if (denied) return { denied };
  const { jid, id } = await params;
  if (!readable(jid)) return { denied: Response.json({ error: "unknown chat" }, { status: 404 }) };
  return { jid, id };
}

export async function GET(req: Request, { params }: { params: Promise<{ jid: string; id: string }> }) {
  const t = await target(req, params);
  if ("denied" in t) return t.denied;
  return Response.json(transcriptState(t.jid, t.id));
}

export async function POST(req: Request, { params }: { params: Promise<{ jid: string; id: string }> }) {
  const t = await target(req, params);
  if ("denied" in t) return t.denied;
  return Response.json(startTranscript(t.jid, t.id));
}
