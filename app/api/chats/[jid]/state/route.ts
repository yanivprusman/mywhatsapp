import { authorize } from "@/lib/auth";
import { findChat } from "@/lib/chats";
import { markSeen, setFilter } from "@/lib/chatState";
import { FILTERS, type Filter } from "@/lib/filters";

/**
 * PUT `{filter?, seen?}` — record, for every device, the filter this chat is
 * open on and/or the newest message just shown in it. `seen` only moves forward.
 */
export async function PUT(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });
  const body = await req.json().catch(() => null);
  if (!body || typeof body !== "object") return Response.json({ error: "body must be a JSON object" }, { status: 400 });
  const { filter, seen } = body as { filter?: unknown; seen?: unknown };
  if (filter === undefined && seen === undefined) return Response.json({ error: "give filter and/or seen" }, { status: 400 });
  if (filter !== undefined && !FILTERS.includes(filter as Filter)) return Response.json({ error: `unknown filter ${filter}` }, { status: 400 });
  if (seen !== undefined && (typeof seen !== "string" || Number.isNaN(Date.parse(seen)))) {
    return Response.json({ error: "seen must be an ISO timestamp" }, { status: 400 });
  }
  try {
    if (filter !== undefined) setFilter(def.id, filter as Filter);
    if (seen !== undefined) markSeen(def.id, seen as string);
    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
