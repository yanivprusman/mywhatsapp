import "server-only";

/**
 * The WhatsApp bridge runs ONLY on the leader (one account = one linked client);
 * every other peer reads it remotely — DB reads through POST /api/dbquery, media
 * through /api/download + /api/mediafile. Never start a bridge for this app.
 */
function config() {
  const url = process.env.WHATSAPP_BRIDGE_URL;
  const token = process.env.WHATSAPP_BRIDGE_TOKEN;
  if (!url || !token) throw new Error("WHATSAPP_BRIDGE_URL / WHATSAPP_BRIDGE_TOKEN missing from .env.local");
  return { url, headers: { Authorization: `Bearer ${token}` } };
}

export async function query(sql: string, params: unknown[] = []): Promise<unknown[][]> {
  const { url, headers } = config();
  const res = await fetch(`${url}/dbquery`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ database: "messages", sql, params }),
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`bridge dbquery: HTTP ${res.status} ${await res.text()}`);
  const body = await res.json();
  if (!body.success) throw new Error(`bridge dbquery: ${body.message}`);
  return body.rows ?? [];
}

export async function sendText(jid: string, message: string): Promise<void> {
  const { url, headers } = config();
  const res = await fetch(`${url}/send`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ recipient: jid, message }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok || !body.success) throw new Error(`bridge send: ${body.message ?? `HTTP ${res.status}`}`);
}

/** Bytes of a media message. The bridge downloads it once and keeps it. */
export async function media(jid: string, id: string): Promise<{ bytes: ArrayBuffer; type: string }> {
  const { url, headers } = config();
  const dl = await fetch(`${url}/download`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ message_id: id, chat_jid: jid }),
  });
  const info = await dl.json().catch(() => ({}));
  if (!info.success || !info.path) throw new Error(`bridge download: ${info.message ?? `HTTP ${dl.status}`}`);
  const file = await fetch(`${url}/mediafile?path=${encodeURIComponent(info.path)}`, { headers });
  if (!file.ok) throw new Error(`bridge mediafile: HTTP ${file.status}`);
  return { bytes: await file.arrayBuffer(), type: file.headers.get("content-type") ?? "application/octet-stream" };
}
