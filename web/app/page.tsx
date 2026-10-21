import { redirect } from "next/navigation";
import { isLoggedIn } from "@/lib/auth";
import { sql } from "@/lib/db";

export const dynamic = "force-dynamic"; // always fetch fresh data

const TZ = "Europe/Dublin";

const APP_NAMES: Record<string, string> = {
  "com.instagram.android": "Instagram",
  "com.google.android.youtube": "YouTube",
  "com.whatsapp": "WhatsApp",
  "com.anthropic.claude": "Claude",
  "com.google.android.gm": "Gmail",
  "com.android.chrome": "Chrome",
  "com.spotify.music": "Spotify",
  "com.linkedin.android": "LinkedIn",
  "com.pinterest": "Pinterest",
  "com.netflix.mediaclient": "Netflix",
  "com.nytimes.crossword": "NYT Crossword",
  "com.sec.android.app.sbrowser": "Samsung Internet",
  "com.nethra.habitapp": "HabitApp",
};
const appName = (pkg: string) => APP_NAMES[pkg] ?? pkg.split(".").pop() ?? pkg;
const time = (d: Date) =>
  d.toLocaleTimeString("en-IE", { timeZone: TZ, hour: "2-digit", minute: "2-digit" });
const duration = (min: number) =>
  min >= 60 ? `${Math.floor(min / 60)}h ${Math.round(min % 60)}m` : `${Math.round(min)}m`;

export default async function Dashboard() {
  if (!(await isLoggedIn())) redirect("/login");

  // All five queries run at the same time
  const [nightRows, todayRows, trendRows, unlockRows, latestRows] = await Promise.all([
    // Last night: the longest screen-off gap between 18:00 yesterday and 12:00 today
    sql`
      WITH bounds AS (
        SELECT
          extract(epoch FROM ((date_trunc('day', now() AT TIME ZONE 'Europe/Dublin') - interval '6 hours') AT TIME ZONE 'Europe/Dublin')) * 1000 AS lo,
          extract(epoch FROM ((date_trunc('day', now() AT TIME ZONE 'Europe/Dublin') + interval '12 hours') AT TIME ZONE 'Europe/Dublin')) * 1000 AS hi
      ),
      s AS (
        SELECT ts, event_type,
               LEAD(ts) OVER (ORDER BY ts) AS next_ts,
               LEAD(event_type) OVER (ORDER BY ts) AS next_type
        FROM usage_events, bounds
        WHERE event_type IN (15, 16) AND ts BETWEEN bounds.lo AND bounds.hi
      )
      SELECT ts AS down_ts, next_ts AS up_ts
      FROM s
      WHERE event_type = 16 AND next_type = 15
      ORDER BY next_ts - ts DESC
      LIMIT 1`,

    // Today's sessions, with categories
    sql`
      SELECT s.package_name, COALESCE(c.category, 'other') AS category, s.started_at, s.minutes
      FROM app_sessions s
      LEFT JOIN app_categories c USING (package_name)
      WHERE s.started_at >= date_trunc('day', now() AT TIME ZONE 'Europe/Dublin') AT TIME ZONE 'Europe/Dublin'
      ORDER BY s.started_at`,

    // Feed minutes and spirals per day, last 14 days
    sql`
      SELECT (s.started_at AT TIME ZONE 'Europe/Dublin')::date::text AS day,
             ROUND(SUM(s.minutes)) AS feed_minutes,
             COUNT(*) FILTER (WHERE s.minutes > 30) AS spirals
      FROM app_sessions s
      JOIN app_categories c USING (package_name)
      WHERE c.category = 'feed' AND s.started_at > now() - interval '14 days'
      GROUP BY 1
      ORDER BY 1`,

    // Unlocks today
    sql`
      SELECT COUNT(*) AS unlocks
      FROM usage_events
      WHERE event_type = 18
        AND ts >= extract(epoch FROM (date_trunc('day', now() AT TIME ZONE 'Europe/Dublin') AT TIME ZONE 'Europe/Dublin')) * 1000`,

    // Newest event, to show how fresh the data is
    sql`SELECT MAX(ts) AS latest FROM usage_events`,
  ]);

  // Last night
  const night = nightRows[0];
  const down = night ? new Date(Number(night.down_ts)) : null;
  const up = night ? new Date(Number(night.up_ts)) : null;
  const awayMin = down && up ? (up.getTime() - down.getTime()) / 60000 : null;

  // Today
  const sessions = todayRows.map((s) => ({
    pkg: String(s.package_name),
    category: String(s.category),
    start: new Date(s.started_at),
    min: Number(s.minutes),
  }));
  const feed = sessions.filter((s) => s.category === "feed");
  const feedMin = feed.reduce((sum, s) => sum + s.min, 0);
  const spirals = feed.filter((s) => s.min > 30).length;
  const firstFeed = up ? feed.find((s) => s.start > up) : undefined;
  const wakeToFeed = up && firstFeed ? (firstFeed.start.getTime() - up.getTime()) / 60000 : null;

  const byApp = new Map<string, number>();
  for (const s of sessions) byApp.set(s.pkg, (byApp.get(s.pkg) ?? 0) + s.min);
  const topApps = [...byApp.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);

  // Trend
  const trend = trendRows.map((r) => ({
    day: String(r.day),
    feed: Number(r.feed_minutes),
    spirals: Number(r.spirals),
  }));
  const maxFeed = Math.max(60, ...trend.map((t) => t.feed));
  const latest = latestRows[0]?.latest ? new Date(Number(latestRows[0].latest)) : null;

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-4 sm:p-8">
      <header className="flex items-baseline justify-between">
        <h1 className="text-2xl font-semibold">Today</h1>
        <p className="text-sm text-neutral-500">Data up to {latest ? time(latest) : "—"}</p>
      </header>

      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Feed time" value={duration(feedMin)} />
        <Stat label="Spirals (30+ min)" value={String(spirals)} />
        <Stat label="Unlocks" value={String(unlockRows[0]?.unlocks ?? 0)} />
        <Stat label="Wake → first feed" value={wakeToFeed === null ? "—" : duration(wakeToFeed)} />
      </section>

      <Card title="Last night">
        {down && up && awayMin !== null ? (
          <p>
            Phone down <b>{time(down)}</b> · screen first on <b>{time(up)}</b> ·{" "}
            <b>{duration(awayMin)}</b> away
          </p>
        ) : (
          <p>Not enough data yet.</p>
        )}
      </Card>

      <Card title="Top apps today">
        <ul className="space-y-1">
          {topApps.map(([pkg, min]) => (
            <li key={pkg} className="flex justify-between">
              <span>{appName(pkg)}</span>
              <span className="tabular-nums">{duration(min)}</span>
            </li>
          ))}
        </ul>
      </Card>

      <Card title="Feed time, last 14 days">
        <div className="flex h-40 items-end gap-1">
          {trend.map((t) => (
            <div
              key={t.day}
              className="flex h-full flex-1 flex-col items-center justify-end gap-1"
              title={`${t.day}: ${t.feed} min, ${t.spirals} spirals`}
            >
              <div
                className="w-full rounded-t bg-orange-500"
                style={{ height: `${(t.feed / maxFeed) * 100}%` }}
              />
              <span className="text-[10px] text-neutral-500">{t.day.slice(8)}</span>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Today's sessions (2 min or longer)">
        <ul className="divide-y divide-neutral-200 text-sm dark:divide-neutral-800">
          {sessions
            .filter((s) => s.min >= 2)
            .reverse()
            .map((s) => (
              <li key={s.start.toISOString() + s.pkg} className="flex justify-between py-1.5">
                <span>
                  {time(s.start)} · {appName(s.pkg)}
                </span>
                <span
                  className={
                    s.category === "feed" && s.min > 30 ? "font-semibold text-orange-500" : ""
                  }
                >
                  {duration(s.min)}
                </span>
              </li>
            ))}
        </ul>
      </Card>
    </main>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
      <p className="text-xs text-neutral-500">{label}</p>
      <p className="text-xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <h2 className="mb-2 text-sm font-medium text-neutral-500">{title}</h2>
      {children}
    </section>
  );
}