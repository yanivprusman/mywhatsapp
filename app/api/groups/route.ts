import { authorize } from "@/lib/auth";
import { CHATS, chatNames, displayName } from "@/lib/chats";
import { conversationNames, isConversation, isDirect } from "@/lib/conversations";
import { readVisibility, setHidden } from "@/lib/visibility";

/**
 * Tools → Groups: every chat the app may show, each with its on/off switch,
 * and for a merged chat each of its groups with its own switch.
 *
 * GET → `{chats: [{id, name, merged, shown, section, sources: [{jid, name, shown}]}]}`
 * PUT `{kind: "chat"|"source"|"conversation", id, shown}` → switch one on or off.
 * `chat`/`source` are the consolidator's (ids in `lib/chats.ts` only);
 * `conversation` is any private chat or group on the Chats side. Conversations are
 * all shown unless hidden, so only the hidden ones are listed (section "chats") —
 * that is where one hidden from the Chats list comes back from.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  try {
    const { hiddenChats, hiddenSources, hiddenConversations } = readVisibility();
    const chats = await Promise.all(CHATS.map(async (def) => {
      const names = def.kind === "merged" ? await chatNames(def.sources) : null;
      return {
        id: def.id,
        // A merged chat's name without the live "· N groups" count — the switches below are the count.
        name: def.kind === "merged" ? def.base : await displayName(def),
        merged: def.kind === "merged",
        shown: !hiddenChats.includes(def.id),
        section: "consolidated",
        sources: def.kind === "merged"
          ? def.sources.map((jid) => ({ jid, name: names?.get(jid) ?? jid, shown: !hiddenSources.includes(jid) }))
          : [],
      };
    }));
    const names = await conversationNames(hiddenConversations);
    for (const id of hiddenConversations) chats.push({ id, name: names.get(id)!, merged: false, shown: false, section: "chats", sources: [] });
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}

export async function PUT(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const body = await req.json().catch(() => null);
  const given = (body ?? {}) as { kind?: unknown; id?: unknown; shown?: unknown };
  const { id, shown } = given;
  if ((given.kind !== "chat" && given.kind !== "source" && given.kind !== "conversation") || typeof id !== "string" || typeof shown !== "boolean") {
    return Response.json({ error: 'body must be {kind: "chat"|"source"|"conversation", id: string, shown: boolean}' }, { status: 400 });
  }
  // An app from before 2026-11-09 hides a private chat as kind "chat".
  const kind = given.kind === "chat" && isDirect(id) ? "conversation" : given.kind;
  const known = kind === "chat"
    ? CHATS.some((c) => c.id === id)
    : kind === "source"
      ? CHATS.some((c) => c.kind === "merged" && c.sources.includes(id))
      : isConversation(id);
  if (!known) return Response.json({ error: `not a ${kind === "source" ? "merged group" : "chat"} this app lists: ${id}` }, { status: 404 });
  try {
    setHidden(kind, id, !shown);
    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
