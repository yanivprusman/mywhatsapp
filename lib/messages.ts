import "server-only";

export type WireMessage = {
  id: string;
  text: string;
  ts: string; // ISO 8601 with offset
  fromMe: boolean;
  sender: string;
  mediaType: string; // "" | image | video | audio | document
  filename: string;
  downloadable: boolean; // the bridge has what it needs to fetch the bytes
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
