import { neon } from "@neondatabase/serverless";
import { NextRequest, NextResponse } from "next/server";

// Connection to your Neon database (the address comes from .env.local / Vercel)
const sql = neon(process.env.DATABASE_URL!);

// The shape of one event, as the phone will send it
type IncomingEvent = {
  ts: number;
  eventType: number;
  packageName: string;
  className: string;
};

// The phone sends events here with a POST request
export async function POST(req: NextRequest) {
  // 1. Reject anyone who doesn't have the secret key
  if (req.headers.get("authorization") !== `Bearer ${process.env.INGEST_TOKEN}`) {
    return NextResponse.json({ error: "unauthorised" }, { status: 401 });
  }

  // 2. Read the list of events from the request
  const events: IncomingEvent[] = await req.json();
  if (!Array.isArray(events) || events.length === 0) {
    return NextResponse.json({ inserted: 0 });
  }
  if (events.length > 5000) {
    return NextResponse.json({ error: "send at most 5000 per request" }, { status: 413 });
  }

  // 3. Insert them all in one SQL statement; duplicates are skipped
  const inserted = await sql.query(
    `INSERT INTO usage_events (ts, event_type, package_name, class_name)
     SELECT * FROM UNNEST($1::bigint[], $2::int[], $3::text[], $4::text[])
     ON CONFLICT DO NOTHING
     RETURNING ts`,
    [
      events.map((e) => e.ts),
      events.map((e) => e.eventType),
      events.map((e) => e.packageName),
      events.map((e) => e.className),
    ]
  );

  // 4. Tell the phone how many were new
  return NextResponse.json({ inserted: inserted.length });
}