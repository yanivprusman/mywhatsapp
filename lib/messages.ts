import "server-only";
import { query } from "@/lib/bridge";

export type WireMessage = {
  id: string;
  text: string;
  ts: string; // ISO 8601 with offset
  fromMe: boolean;
  sender: string;
  mediaType: string; // "" | image | video | audio | document
  filename: string;
  downloadable: boolean; // the bridge has what it needs to fetch the bytes
  senderName?: string; // as WhatsApp shows it: saved name, else "~ pushname", else +phone
};

export const MESSAGE_COLUMNS =
  "id, content, timestamp, is_from_me, sender, media_type, filename, " +
  "(url IS NOT NULL AND url != '' AND media_key IS NOT NULL AND length(media_key) > 0)";

export function toMessage(r: unknown[]): WireMessage {
  return {
    id: String(r[0]),
    text: String(r[1] ?? ""),
    ts: String(r[2]),
    fromMe: Boolean(r[3]),
    sender: String(r[4] ?? ""),
    mediaType: String(r[5] ?? ""),
    filename: String(r[6] ?? ""),
    downloadable: Boolean(r[7]),
  };
}

/** The bridge returns timestamps as ISO ("…T…"); sqlite stores them with a space. */
export function toDbTime(iso: string): string {
  return iso.replace("T", " ");
}

/**
 * Fill senderName the way WhatsApp labels group senders: the saved contact
 * name, else "~ <their own push name>", else the number. Some old rows carry
 * the group's own jid as sender (the bridge didn't know who); those are "Unknown".
 */
export async function withSenderNames(messages: WireMessage[]): Promise<WireMessage[]> {
  const phones = [...new Set(messages.filter((m) => !m.fromMe && /^\d{6,15}$/.test(m.sender)).map((m) => m.sender))];
  const names = new Map<string, string>();
  for (let i = 0; i < phones.length; i += 200) {
    const batch = phones.slice(i, i + 200);
    const rows = await query(
      `SELECT their_jid, full_name, push_name FROM whatsmeow_contacts WHERE their_jid IN (${batch.map(() => "?").join(",")})`,
      batch.map((p) => `${p}@s.whatsapp.net`),
      "whatsmeow",
    );
    for (const [jid, full, push] of rows) {
      const phone = String(jid).split("@")[0];
      if (full) names.set(phone, String(full));
      else if (push) names.set(phone, `~ ${push}`);
    }
  }
  return messages.map((m) => ({
    ...m,
    senderName: m.fromMe ? "You" : /^\d{6,15}$/.test(m.sender) ? names.get(m.sender) ?? `+${m.sender}` : "Unknown",
  }));
}
