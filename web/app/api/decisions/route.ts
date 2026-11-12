import { NextRequest, NextResponse } from "next/server";
import { sql } from "@/lib/db";

const ACTIONS = ["nudged", "quiet_low_risk", "quiet_hourly_cap"];

// One robot decision, as the phone sends it
type Decision = {
    decidedMs: number;
    episodeStartMs: number;
    firstApp: string;
    firstCategory: string;
    activeMinutes: number;
    risk: number;
    threshold: number;
    modelId: number;
    action: string;
    line: string | null;
};

// POST: the phone sends a list of decisions (it batches them if it was offline)
export async function POST(req: NextRequest) {
    if (req.headers.get("authorization") !== `Bearer ${process.env.INGEST_TOKEN}`) {
        return NextResponse.json({ error: "unauthorised" }, { status: 401 });
    }

    const list: Decision[] = await req.json();
    if (!Array.isArray(list) || list.some((d) => !ACTIONS.includes(d.action))) {
        return NextResponse.json({ error: "bad decisions" }, { status: 400 });
    }
    if (list.length === 0) return NextResponse.json({ inserted: 0 });

    // Same trick as /api/events: one INSERT for the whole list; repeats are skipped
    const inserted = await sql.query(
        `INSERT INTO robot_decisions (decided_at, episode_start, first_app, first_category,
                                  active_minutes, risk, threshold, model_id, action, line)
     SELECT to_timestamp(d / 1000.0), to_timestamp(e / 1000.0), a, c, m, r, t, mid, act, l
     FROM UNNEST($1::bigint[], $2::bigint[], $3::text[], $4::text[], $5::numeric[],
                 $6::float8[], $7::float8[], $8::int[], $9::text[], $10::text[])
          AS x(d, e, a, c, m, r, t, mid, act, l)
     ON CONFLICT (episode_start) DO NOTHING
     RETURNING id`,
        [
            list.map((d) => d.decidedMs),
            list.map((d) => d.episodeStartMs),
            list.map((d) => d.firstApp),
            list.map((d) => d.firstCategory),
            list.map((d) => d.activeMinutes),
            list.map((d) => d.risk),
            list.map((d) => d.threshold),
            list.map((d) => d.modelId),
            list.map((d) => d.action),
            list.map((d) => d.line),
        ]
    );

    return NextResponse.json({ inserted: inserted.length });
}