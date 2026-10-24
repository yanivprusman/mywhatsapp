import { authorize } from "@/lib/auth";
import { query } from "@/lib/bridge";
import { CHATS, chatNames, displayName, sourcesOf } from "@/lib/chats";
import { clearedAt, laterOf } from "@/lib/cleared";
import { FILTERS, type Filter, matches } from "@/lib/filters";
import { loadMessages } from "@/lib/merged";
import { MESSAGE_COLUMNS, toDbTime, toMessage, withSenderNames } from "@/lib/messages";

/**
 * `seen=<json {chatId: ts}>` — the newest message the phone has shown per chat.
 * Each chat's `unread` counts others' messages after that; a chat the phone has
 * never opened counts 0, so a fresh install isn't buried in history.
 * A cleared chat's last line and unread count start after its cutoff.
 * `filters=<json {chatId: filter}>` — the filter each chat is open on. A filtered
 * chat's last line and unread count are of matching messages only, so a chat on
 * Wanted is not announced by a "good morning".
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  let seen: Record<string, string> = {};
  try {
    seen = JSON.parse(new URL(req.url).searchParams.get("seen") ?? "{}");
  } catch {
    return Response.json({ error: "seen must be a JSON object of chat id → timestamp" }, { status: 400 });
  }
  let filters: Record<string, Filter> = {};
  try {
    filters = JSON.parse(new URL(req.url).searchParams.get("filters") ?? "{}");
  } catch {
    return Response.json({ error: "filters must be a JSON object of chat id → filter" }, { status: 400 });
  }
  const bad = Object.values(filters).find((f) => !FILTERS.includes(f));
  if (bad) return Response.json({ error: `unknown filter ${bad}` }, { status: 400 });
  try {
    const chats = await Promise.all(
      CHATS.map(async (def) => {
        const sources = sourcesOf(def);
        const cutoff = clearedAt(def.id);
        const inList = `chat_jid IN (${sources.map(() => "?").join(",")})` + (cutoff ? " AND timestamp > ?" : "");
        const listArgs = cutoff ? [...sources, toDbTime(cutoff)] : sources;
        const since = laterOf(seen[def.id], cutoff);
        const filter = filters[def.id] ?? "all";
        let last: ReturnType<typeof toMessage> | undefined;
        let unread: number;
        if (filter === "all") {
          const [row] = await query(
            `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE ${inList} ORDER BY timestamp DESC LIMIT 1`,
            listArgs,
          );
          last = row ? toMessage(row) : undefined;
          const [count] = since
            ? await query(`SELECT COUNT(*) FROM messages WHERE ${inList} AND timestamp > ? AND NOT is_from_me`, [...listArgs, toDbTime(since)])
            : [[0]];
          unread = Number(count?.[0] ?? 0);
        } else {
          // Filters classify text, which SQL can't: the same messages the chat shows (reposts collapsed), newest first.
          const hits = (await loadMessages(def, cutoff ? { after: cutoff } : {})).filter((m) => matches(filter, m));
          last = hits[0];
          unread = since ? hits.filter((m) => m.ts > since && !m.fromMe).length : 0;
        }
        const names = def.kind === "merged" ? await chatNames(sources) : null;
        return {
          jid: def.id,
          name: await displayName(def),
          isGroup: def.kind === "merged" || def.id.endsWith("@g.us"),
          merged: def.kind === "merged",
          canSend: def.kind === "chat" && def.canSend,
          sources: names ? sources.map((j) => ({ jid: j, name: names.get(j) ?? j })) : [],
          clearedAt: cutoff,
          filter,
          unread,
          last: last ? (await withSenderNames([last]))[0] : null,
        };
      }),
    );
    chats.sort((a, b) => (b.last?.ts ?? "").localeCompare(a.last?.ts ?? ""));
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
