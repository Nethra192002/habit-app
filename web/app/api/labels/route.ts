import { NextRequest, NextResponse } from "next/server";
import { sql } from "@/lib/db";

// Same secret key the phone already uses for /api/events
function authorised(req: NextRequest) {
  return req.headers.get("authorization") === `Bearer ${process.env.INGEST_TOKEN}`;
}

// GET: the phone asks "which feed sessions should I ask about?"
// Feed sessions of 10+ minutes from the last 2 days that have no answer yet.
// We ask about medium sessions too, not only long ones, so the model also
// learns what a session that went as planned looks like.
export async function GET(req: NextRequest) {
  if (!authorised(req)) {
    return NextResponse.json({ error: "unauthorised" }, { status: 401 });
  }

  const rows = await sql.query(
    `SELECT s.package_name,
            (EXTRACT(EPOCH FROM s.started_at) * 1000)::bigint AS started_ms,
            ROUND(s.minutes) AS minutes
     FROM app_sessions s
     JOIN app_categories c USING (package_name)
     LEFT JOIN session_labels l
       ON l.session_start = s.started_at AND l.package_name = s.package_name
     WHERE c.category = 'feed'
       AND s.minutes >= 10
       AND s.started_at > now() - interval '2 days'
       AND l.package_name IS NULL
     ORDER BY s.started_at DESC
     LIMIT 20`
  );

  return NextResponse.json(
    rows.map((r) => ({
      packageName: r.package_name,
      startedMs: Number(r.started_ms),
      minutes: Number(r.minutes),
    }))
  );
}

// POST: the phone sends one answer — did this session run longer than meant?
type IncomingLabel = {
  startedMs: number;
  packageName: string;
  overran: boolean;
};

export async function POST(req: NextRequest) {
  if (!authorised(req)) {
    return NextResponse.json({ error: "unauthorised" }, { status: 401 });
  }

  const label: IncomingLabel = await req.json();
  if (
    typeof label.startedMs !== "number" ||
    typeof label.packageName !== "string" ||
    typeof label.overran !== "boolean"
  ) {
    return NextResponse.json({ error: "bad label" }, { status: 400 });
  }

  // Answering the same session twice replaces the old answer
  await sql.query(
    `INSERT INTO session_labels (session_start, package_name, overran)
     VALUES (to_timestamp($1 / 1000.0), $2, $3)
     ON CONFLICT (session_start, package_name)
     DO UPDATE SET overran = EXCLUDED.overran, labelled_at = now()`,
    [label.startedMs, label.packageName, label.overran]
  );

  return NextResponse.json({ ok: true });
}