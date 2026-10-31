import { createClient } from '@supabase/supabase-js';
import { createApp } from './app.js';
import { readConfig } from './config.js';

const config = readConfig();
const supabase = createClient(config.supabaseUrl, config.supabaseKey, {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
});
const app = createApp({ supabase, apiToken: config.apiToken });
const server = app.listen(config.port, () => {
  console.log(`Job application API listening on port ${config.port}`);
});
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 10000).unref();
  });
}
