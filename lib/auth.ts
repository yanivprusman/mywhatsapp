import "server-only";
import { timingSafeEqual } from "node:crypto";

/**
 * Bearer-token guard for every API route. The server listens on 0.0.0.0 (the
 * WireGuard overlay AND the home LAN) and serves private messages and a send
 * route from the owner's own number, so nothing is open. A missing token in
 * the environment refuses everything rather than guarding nothing.
 */
export function authorize(req: Request): Response | null {
  const secret = process.env.MYWHATSAPP_API_TOKEN ?? "";
  const given = (req.headers.get("authorization") ?? "").replace(/^Bearer /, "");
  const a = Buffer.from(given);
  const b = Buffer.from(secret);
  if (!secret || a.length !== b.length || !timingSafeEqual(a, b)) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  return null;
}
