import "server-only";
import { execFile } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { media } from "@/lib/bridge";

const run = promisify(execFile);

/**
 * Voice messages turned into text, on request.
 *
 * Speech to text goes through the machine's ONE transcriber, `/root/bin/transcribe`
 * (the resident whisperd worker, large-v3) — the same path whoCalled uses. There is
 * no second way: if whisperd is down the request fails saying so.
 *
 * A transcript is made once and kept (data/transcripts.json), keyed by chat + message,
 * so every device reads the same text and nothing is transcribed twice. A job runs in
 * the background: whisperd runs at roughly half real time and is one queue, so a long
 * note can take minutes — longer than the phone should hold a request open. The phone
 * starts the job, then asks for the result.
 */
const FILE = path.join(process.cwd(), "data", "transcripts.json");
const TRANSCRIBE_BIN = "/root/bin/transcribe";
// Whisper is asked to detect among the languages actually spoken here; leaving it fully
// open lets a short clip be heard as some third language.
const LANGS = "he,en";

type Saved = { text: string; at: string };
export type TranscriptState =
  | { status: "done"; text: string }
  | { status: "working" }
  | { status: "failed"; error: string }
  | { status: "none" };

const keyOf = (jid: string, id: string) => `${jid}|${id}`;
const running = new Map<string, Promise<void>>();
const failures = new Map<string, string>();

function readAll(): Record<string, Saved> {
  try {
    return JSON.parse(readFileSync(FILE, "utf8"));
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return {};
    throw e;
  }
}

function save(key: string, text: string) {
  const all = readAll();
  all[key] = { text, at: new Date().toISOString() };
  mkdirSync(path.dirname(FILE), { recursive: true });
  writeFileSync(FILE + ".tmp", JSON.stringify(all, null, 2));
  renameSync(FILE + ".tmp", FILE);
}

/** The saved transcripts of these messages, by message id. */
export function transcriptsFor(jid: (id: string) => string, ids: string[]): Map<string, string> {
  const all = readAll();
  const out = new Map<string, string>();
  for (const id of ids) {
    const t = all[keyOf(jid(id), id)];
    if (t) out.set(id, t.text);
  }
  return out;
}

export function transcriptState(jid: string, id: string): TranscriptState {
  const key = keyOf(jid, id);
  const saved = readAll()[key];
  if (saved) return { status: "done", text: saved.text };
  if (running.has(key)) return { status: "working" };
  const error = failures.get(key);
  if (error) return { status: "failed", error };
  return { status: "none" };
}

/** Start transcribing (a no-op if it is done or already running). */
export function startTranscript(jid: string, id: string): TranscriptState {
  const now = transcriptState(jid, id);
  if (now.status === "done" || now.status === "working") return now;
  const key = keyOf(jid, id);
  failures.delete(key);
  const job = transcribe(jid, id)
    .then((text) => save(key, text))
    .catch((e) => {
      failures.set(key, (e as Error).message);
    })
    .finally(() => running.delete(key));
  running.set(key, job);
  return { status: "working" };
}

async function transcribe(jid: string, id: string): Promise<string> {
  let audio: ArrayBuffer;
  try {
    ({ bytes: audio } = await media(jid, id));
  } catch (e) {
    throw new Error(`The voice message can't be fetched: ${(e as Error).message}`);
  }
  const dir = mkdtempSync(path.join(tmpdir(), "mywhatsapp-voice-"));
  const file = path.join(dir, "voice.ogg");
  try {
    writeFileSync(file, Buffer.from(audio));
    // whisperd's client enforces WHISPERD_TIMEOUT itself; ours outlives it so its error is the one reported.
    const waitS = 900;
    const { stdout } = await run(TRANSCRIBE_BIN, [file, LANGS, "transcribe"], {
      env: { ...process.env, WHISPERD_TIMEOUT: String(waitS) },
      timeout: (waitS + 20) * 1000,
      maxBuffer: 16 * 1024 * 1024,
      encoding: "utf8",
    });
    const text = stdout.trim();
    if (!text) throw new Error("No speech was heard in the voice message");
    return text;
  } catch (e) {
    const err = e as NodeJS.ErrnoException & { stderr?: string; killed?: boolean };
    if (err.killed) throw new Error("Transcription timed out");
    if (err.code === "ENOENT") throw new Error(`${TRANSCRIBE_BIN} is missing on this machine`);
    throw new Error(err.stderr?.trim() || err.message);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
