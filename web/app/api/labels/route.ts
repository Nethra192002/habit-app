import { NextRequest, NextResponse } from "next/server";
import { sql } from "@/lib/db";

// Same secret key the phone already uses for /api/events
function authorised(req: NextRequest) {
  return req.headers.get("authorization") === `Bearer ${process.env.INGEST_TOKEN}`;
}

// GET: the phone asks "which feed episodes should I ask about?"
// Episodes with 10+ feed minutes from the last 2 days that have no answer yet.
// Medium ones are included too, so the model also learns what "fine" looks like.
export async function GET(req: NextRequest) {
  if (!authorised(req)) {
    return NextResponse.json({ error: "unauthorised" }, { status: 401 });
  }

  const rows = await sql.query(
    `SELECT (EXTRACT(EPOCH FROM e.started_at) * 1000)::bigint AS started_ms,
            (EXTRACT(EPOCH FROM e.ended_at) * 1000)::bigint   AS ended_ms,
            ROUND(e.feed_minutes) AS minutes,
            e.apps
     FROM feed_episodes e
     LEFT JOIN episode_labels l ON l.episode_start = e.started_at
     WHERE e.feed_minutes >= 10
       AND e.started_at > now() - interval '2 days'
       AND l.episode_start IS NULL
     ORDER BY e.started_at DESC
     LIMIT 20`
  );

  return NextResponse.json(
    rows.map((r) => ({
      startedMs: Number(r.started_ms),
      endedMs: Number(r.ended_ms),
      minutes: Number(r.minutes),
      apps: r.apps as string,
    }))
  );
}

// POST: the phone sends one answer about an episode
const OUTCOMES = ["intended", "bit_over", "lost_track", "not_real"];

type IncomingLabel = {
  startedMs: number;
  endedMs: number;
  outcome: string;
};

export async function POST(req: NextRequest) {
  if (!authorised(req)) {
    return NextResponse.json({ error: "unauthorised" }, { status: 401 });
  }

  const label: IncomingLabel = await req.json();
  if (
    typeof label.startedMs !== "number" ||
    typeof label.endedMs !== "number" ||
    !OUTCOMES.includes(label.outcome)
  ) {
    return NextResponse.json({ error: "bad label" }, { status: 400 });
  }

  // Answering the same episode twice replaces the old answer
  await sql.query(
    `INSERT INTO episode_labels (episode_start, episode_end, outcome)
     VALUES (to_timestamp($1 / 1000.0), to_timestamp($2 / 1000.0), $3)
     ON CONFLICT (episode_start)
     DO UPDATE SET episode_end = EXCLUDED.episode_end,
                   outcome = EXCLUDED.outcome,
                   labelled_at = now()`,
    [label.startedMs, label.endedMs, label.outcome]
  );

  return NextResponse.json({ ok: true });
}