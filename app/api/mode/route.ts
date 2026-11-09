import { authorize } from "@/lib/auth";
import { MODES, type Mode, readMode, writeMode } from "@/lib/appMode";

/** The app's mode (lib/appMode.ts). GET → `{mode}`; PUT `{mode: "groups"|"private"}`. */
export async function GET(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  return Response.json({ mode: readMode() });
}

export async function PUT(req: Request) {
  const denied = authorize(req);
  if (denied) return denied;
  const { mode } = ((await req.json().catch(() => null)) ?? {}) as { mode?: unknown };
  if (!MODES.includes(mode as Mode)) return Response.json({ error: 'body must be {mode: "groups"|"private"}' }, { status: 400 });
  try {
    writeMode(mode as Mode);
    return Response.json({ mode });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}
