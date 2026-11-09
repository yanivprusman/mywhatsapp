import { authorize } from "@/lib/auth";
import { CHATS, chatNames, displayName } from "@/lib/chats";
import { directNames, isDirect } from "@/lib/direct";
import { readVisibility, setHidden } from "@/lib/visibility";

/**
 * Tools → Groups: every chat the app may show, each with its on/off switch,
 * and for a merged chat each of its groups with its own switch.
 *
 * GET → `{chats: [{id, name, merged, shown, sources: [{jid, name, shown}]}]}`
 * PUT `{kind: "chat"|"source", id, shown}` → switch one on or off.
 * Only ids already in the allowlist (`lib/chats.ts`) or private chats are accepted.
 * Private chats are all shown unless hidden, so only the hidden ones are listed —
 * that is where a private chat hidden from the chat list comes back from.
 */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  try {
    const { hiddenChats, hiddenSources } = readVisibility();
    const chats = await Promise.all(CHATS.map(async (def) => {
      const names = def.kind === "merged" ? await chatNames(def.sources) : null;
      return {
        id: def.id,
        // A merged chat's name without the live "· N groups" count — the switches below are the count.
        name: def.kind === "merged" ? def.base : await displayName(def),
        merged: def.kind === "merged",
        shown: !hiddenChats.includes(def.id),
        sources: def.kind === "merged"
          ? def.sources.map((jid) => ({ jid, name: names?.get(jid) ?? jid, shown: !hiddenSources.includes(jid) }))
          : [],
      };
    }));
    const hiddenDirect = hiddenChats.filter(isDirect);
    const names = await directNames(hiddenDirect);
    for (const id of hiddenDirect) chats.push({ id, name: names.get(id)!, merged: false, shown: false, sources: [] });
    return Response.json({ chats });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}

export async function PUT(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const body = await req.json().catch(() => null);
  const { kind, id, shown } = (body ?? {}) as { kind?: unknown; id?: unknown; shown?: unknown };
  if ((kind !== "chat" && kind !== "source") || typeof id !== "string" || typeof shown !== "boolean") {
    return Response.json({ error: 'body must be {kind: "chat"|"source", id: string, shown: boolean}' }, { status: 400 });
  }
  const known = kind === "chat"
    ? isDirect(id) || CHATS.some((c) => c.id === id)
    : CHATS.some((c) => c.kind === "merged" && c.sources.includes(id));
  if (!known) return Response.json({ error: `not a ${kind === "chat" ? "chat" : "merged group"} this app lists: ${id}` }, { status: 404 });
  try {
    setHidden(kind, id, !shown);
    return Response.json({ ok: true });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
