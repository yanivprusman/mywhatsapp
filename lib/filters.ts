/**
 * In-chat message filters. Classification is by the post's opening words —
 * work groups are written in set phrases ("דרוש גולש…", "מנהל מקצועי פנוי…"),
 * so the first line says what kind of post it is far better than the body.
 */
export const FILTERS = ["all", "wanted", "available", "media", "links"] as const;
export type Filter = (typeof FILTERS)[number];

export type Classifiable = { text: string; mediaType: string };

const DIRECTION_MARKS = /[‎‏‪-‮⁦-⁩]/g;
/** WhatsApp formatting (*bold*, _italic_, ~strike~) wraps words and hides them from \s boundaries. */
const FORMATTING = /[*_~]/g;

/** Who a job post asks for — "מחפש עגלה" is not a job, "מחפש גולש" is. */
const WORKER =
  "(גולש|גולשת|גולשים|עובד|עובדת|עובדים|מגיש|מגישה|מגישים|צופה|צופים|צוות|מנהל|מנהלי|קבלן|קבלני|איש|אנשי|מישהו|בעל מקצוע|בעלי מקצוע|עוזר|עוזרים|טכנאי|מתקין|ידיים|פרילנס|פרילנסר|פרילנסרים|רתך|רתכים|מומחה|מומחים|מקצוען|מקצוענים|הצעת מחיר|הצעות מחיר)";

const WANTED = [
  // "דרוש", "דרושה", "דרושים", "דרושות" anywhere in the opening
  /(^|[\s,.!:;()"'״׳\-–/])דרוש(ה|ים|ות)?([\s,.!:;()"'״׳\-–/]|$)/,
  /(^|[\s,.!:;()"'״׳\-–/])נדרש(ה|ים|ות)?([\s,.!:;()"'״׳\-–/]|$)/,
  // "מחפש … גולש", "צריך … מגיש" — a looking-for verb followed by a worker
  new RegExp(`(^|[\\s,.!:;()"'״׳\\-–/])(מחפש|מחפשת|מחפשים|צריך|צריכה|צריכים)\\s(.{0,40}?\\s)?${WORKER}`),
];

/** "מי שצריך מנהל…" is a worker offering himself, not a job. */
const OFFERING_TO = /(^|\s)מי ש(צריך|מחפש|צריכה|מחפשת)/;

const AVAILABLE = /(^|[\s,.!:;()"'״׳\-–/])(פנוי|פנויה|פנויים|פנוים|פנויות|מתפנה|מתפנים|זמין|זמינה|זמינים)([\s,.!:;()"'״׳\-–/]|$)/;

const LINK = /https?:\/\/\S+/;

function opening(text: string): string {
  return text.replace(DIRECTION_MARKS, "").replace(FORMATTING, "").trim().slice(0, 120);
}

function firstIndex(o: string, rs: RegExp[]): number {
  const hits = rs.map((r) => o.search(r)).filter((i) => i >= 0);
  return hits.length ? Math.min(...hits) : -1;
}

/** Whichever signal comes first decides: "מחפש גולש פנוי" is a job, "גולש פנוי, מי שצריך…" is a worker. */
export function isWanted(m: Classifiable): boolean {
  const o = opening(m.text);
  if (!o || /^נסגר/.test(o)) return false;
  const w = firstIndex(o, WANTED);
  if (w < 0) return false;
  const a = firstIndex(o, [AVAILABLE, OFFERING_TO]);
  return a < 0 || w < a;
}

export function isAvailable(m: Classifiable): boolean {
  const o = opening(m.text);
  return !!o && (AVAILABLE.test(o) || OFFERING_TO.test(o)) && !isWanted(m);
}

export function matches(filter: Filter, m: Classifiable): boolean {
  switch (filter) {
    case "all": return true;
    case "wanted": return isWanted(m);
    case "available": return isAvailable(m);
    case "media": return m.mediaType === "image" || m.mediaType === "video";
    case "links": return LINK.test(m.text);
  }
}
