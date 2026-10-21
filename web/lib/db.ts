import { neon } from "@neondatabase/serverless";

// One shared database connection for the whole web app
export const sql = neon(process.env.DATABASE_URL!);