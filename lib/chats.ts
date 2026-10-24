import "server-only";

/**
 * The chats MyWhatsApp shows. An allowlist on purpose: the bridge holds the
 * whole account, and this app (and its send route) must reach only what was
 * added here — a chat is added by name, deliberately, one at a time.
 */
export const CHATS: { jid: string }[] = [
  { jid: "120363427371815253@g.us" }, // קבוצה ריקה — the owner's own preview group
  { jid: "972509974742-1431932281@g.us" }, // סנפלינג — rope-access jobs board (wanted / available posts)
];

export function allowed(jid: string): boolean {
  return CHATS.some((c) => c.jid === jid);
}
