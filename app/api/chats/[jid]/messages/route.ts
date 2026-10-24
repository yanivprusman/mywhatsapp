import { authorize } from "@/lib/auth";
import { findChat } from "@/lib/chats";
import { FILTERS, type Filter, isAvailable, isWanted, matches } from "@/lib/filters";
import { loadMessages } from "@/lib/merged";
import { withSenderNames } from "@/lib/messages";

/**
 * Newest first. `before=<ts>` pages back into history; `after=<ts>` returns only
 * what arrived since (the phone polls with it while a chat is open).
 * `filter=wanted|available|media|links` narrows to one kind of post; filtering
 * (and, for merged chats, collapsing reposts) runs over the whole chat, so a page
 * is always `limit` matches deep. The first page also carries `counts` per filter.
 */
export async function GET(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });

  const sp = new URL(req.url).searchParams;
  const limit = Math.min(Number(sp.get("limit") ?? 50) || 50, 200);
  const before = sp.get("before");
  const after = sp.get("after");
  const filter = (sp.get("filter") ?? "all") as Filter;
  if (!FILTERS.includes(filter)) return Response.json({ error: `unknown filter ${filter}` }, { status: 400 });

  try {
    // Reposts can only be collapsed against the whole history, so pages load it all and cut by time.
    const all = await loadMessages(def, after ? { after } : {});
    const inRange = before ? all.filter((m) => m.ts < before) : all;
    const hits = inRange.filter((m) => matches(filter, m));
    const messages = (await withSenderNames(hits.slice(0, limit))).map((m) => ({
      ...m,
      kind: isWanted(m) ? "wanted" : isAvailable(m) ? "available" : null,
    }));
    const counts = !before && !after
      ? Object.fromEntries(FILTERS.map((f) => [f, all.filter((m) => matches(f, m)).length]))
      : undefined;
    return Response.json({ messages, hasMore: !after && hits.length > limit, counts });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
