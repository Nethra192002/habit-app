import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE } from "@/lib/auth";

// Runs on the server when the form is submitted
async function login(formData: FormData) {
  "use server";
  if (formData.get("password") !== process.env.DASHBOARD_PASSWORD) {
    redirect("/login?error=1");
  }
  (await cookies()).set(SESSION_COOKIE, process.env.SESSION_SECRET!, {
    httpOnly: true,            // page scripts can't read it
    secure: true,              // only sent over HTTPS
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 30, // stay logged in for 30 days
  });
  redirect("/");
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  return (
    <main className="mx-auto mt-24 max-w-xs space-y-4 p-4">
      <h1 className="text-xl font-semibold">Habit dashboard</h1>
      <form action={login} className="space-y-3">
        <input
          type="password"
          name="password"
          placeholder="Password"
          className="w-full rounded border border-neutral-400 bg-transparent px-3 py-2"
          autoFocus
        />
        <button className="w-full rounded bg-orange-500 px-3 py-2 font-medium text-white">
          Log in
        </button>
      </form>
      {error && <p className="text-sm text-red-500">Wrong password.</p>}
    </main>
  );
}