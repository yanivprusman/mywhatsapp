import { authorize } from "@/lib/auth";
import { findChat } from "@/lib/chats";
import { type Round, suggestReplies } from "@/lib/suggest";

/**
 * The reply helper, private chats only (never a group).
 * POST `{said?: string, rounds?: [{said, drafts: [{label, text}]}]}` →
 * `{suggestions: [{label, text}]}`. `said` is what the owner tells the helper
 * now; `rounds` is the conversation with it so far, kept by the phone.
 * Only drafts — nothing is sent.
 */
export async function POST(req: Request, { params }: { params: Promise<{ jid: string }> }) {
  const denied = authorize(req);
  if (denied) return denied;
  const { jid } = await params;
  const def = findChat(jid);
  if (!def) return Response.json({ error: "unknown chat" }, { status: 404 });
  if (def.kind !== "direct") return Response.json({ error: "the reply helper is for private chats only" }, { status: 400 });
  const body = (await req.json().catch(() => ({}))) as { said?: unknown; rounds?: unknown };
  const said = typeof body.said === "string" ? body.said.slice(0, 2000) : "";
  const rounds = (Array.isArray(body.rounds) ? body.rounds : []).slice(-8).flatMap((r): Round[] => {
    const { said: s, drafts } = (r ?? {}) as { said?: unknown; drafts?: unknown };
    if (!Array.isArray(drafts)) return [];
    return [{
      said: typeof s === "string" ? s.slice(0, 2000) : "",
      drafts: drafts.flatMap((d) => {
        const { label, text } = (d ?? {}) as { label?: unknown; text?: unknown };
        return typeof text === "string" ? [{ label: typeof label === "string" ? label : "", text: text.slice(0, 2000) }] : [];
      }),
    }];
  });
  try {
    return Response.json({ suggestions: await suggestReplies(def.id, rounds, said) });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
