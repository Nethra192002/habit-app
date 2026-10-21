import { cookies } from "next/headers";
import { timingSafeEqual } from "crypto";

export const SESSION_COOKIE = "habit_session";

// Compares two strings in a way that doesn't leak hints through timing
function same(a: string, b: string) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

// True if the browser has the login cookie
export async function isLoggedIn() {
  const value = (await cookies()).get(SESSION_COOKIE)?.value ?? "";
  return same(value, process.env.SESSION_SECRET ?? "not-set");
}