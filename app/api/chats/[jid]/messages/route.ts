import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { allowed } from "@/lib/chats";
import { FILTERS, type Filter, isAvailable, isWanted, matches } from "@/lib/filters";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * Newest first. `before=<ts>` pages back into history; `after=<ts>` returns only
 * what arrived since (the phone polls with it while a chat is open).
 * `filter=wanted|available|media|links` narrows to one kind of post; filtering
 * runs here over the whole chat, so a page is always `limit` matches deep.
 * The first page (no before/after) also carries `counts` per filter.
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
  const filter = (sp.get("filter") ?? "all") as Filter;
  if (!FILTERS.includes(filter)) return Response.json({ error: `unknown filter ${filter}` }, { status: 400 });
  const firstPage = !before && !after;

  let where = "chat_jid = ?";
  const args: unknown[] = [jid];
  if (before) { where += " AND timestamp < ?"; args.push(toDbTime(before)); }
  if (after) { where += " AND timestamp > ?"; args.push(toDbTime(after)); }
  // Unfiltered pages are a plain LIMIT; filtered ones (and the counts) need the whole range.
  const needAll = filter !== "all" || firstPage;
  try {
    const rows = await query(
      `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE ${where} ORDER BY timestamp DESC` + (needAll ? "" : " LIMIT ?"),
      needAll ? args : [...args, limit],
    );
    const all = rows.map(toMessage);
    const hits = all.filter((m) => matches(filter, m));
    const messages = (await withSenderNames(hits.slice(0, limit))).map((m) => ({
      ...m,
      kind: isWanted(m) ? "wanted" : isAvailable(m) ? "available" : null,
    }));
    const counts = firstPage
      ? Object.fromEntries(FILTERS.map((f) => [f, all.filter((m) => matches(f, m)).length]))
      : undefined;
    return Response.json({ messages, hasMore: !after && hits.length > limit, counts });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
