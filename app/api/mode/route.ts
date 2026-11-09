import { authorize } from "@/lib/auth";
import { MODES, type Mode, readMode, writeMode } from "@/lib/appMode";

/** The app's mode (lib/appMode.ts). GET → `{mode}`; PUT `{mode: "consolidated"|"chats"}`. */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  return Response.json({ mode: readMode() });
}

export async function PUT(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const given = (((await req.json().catch(() => null)) ?? {}) as { mode?: unknown }).mode;
  const mode = given === "groups" ? "consolidated" : given === "private" ? "chats" : given; // an app from before 2026-11-09
  if (!MODES.includes(mode as Mode)) return Response.json({ error: 'body must be {mode: "consolidated"|"chats"}' }, { status: 400 });
  try {
    writeMode(mode as Mode);
    return Response.json({ mode });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
