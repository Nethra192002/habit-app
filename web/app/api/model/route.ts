import { NextRequest, NextResponse } from "next/server";
import { sql } from "@/lib/db";

// GET: the phone downloads the newest risk model (same secret key as the other APIs)
export async function GET(req: NextRequest) {
    if (req.headers.get("authorization") !== `Bearer ${process.env.INGEST_TOKEN}`) {
        return NextResponse.json({ error: "unauthorised" }, { status: 401 });
    }

    const rows = await sql.query(
        `SELECT id, created_at, params FROM models
     WHERE name = 'risk_v1'
     ORDER BY created_at DESC
     LIMIT 1`
    );
    if (rows.length === 0) {
        return NextResponse.json({ error: "no model yet" }, { status: 404 });
    }

    // id lets the phone tell whether it already has this version
    return NextResponse.json({ id: rows[0].id, ...rows[0].params });
}