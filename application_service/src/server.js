import { createClient } from '@supabase/supabase-js';
import { createApp } from './app.js';
import { readConfig } from './config.js';
import { createInMemorySupabase } from './inMemoryDb.js';

let config;
let supabase;

const shouldUseInMemory =
  process.env.USE_IN_MEMORY === 'true' ||
  !process.env.SUPABASE_URL ||
  /REPLACE|YOUR_PROJECT/.test(process.env.SUPABASE_URL);

if (shouldUseInMemory) {
  const port = Number(process.env.PORT || 3002);
  const apiToken = process.env.API_ACCESS_TOKEN || 'hire2hack-recruiter-demo-api-token-2026-v1';
  console.log(`[application_service] Starting with embedded database (seed data loaded) on port ${port}`);
  config = { port, apiToken };
  supabase = createInMemorySupabase();
} else {
  config = readConfig();
  supabase = createClient(config.supabaseUrl, config.supabaseKey, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}

const app = createApp({ supabase, apiToken: config.apiToken });
const server = app.listen(config.port, () => {
  console.log(`Job application API listening on port ${config.port}`);
  console.log(`Recruiter UI: http://localhost:${config.port}`);
  console.log(`Recruiter API Access Token: ${config.apiToken}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 10000).unref();
  });
}
