export function readConfig(env = process.env) {
  for (const name of ['SUPABASE_URL', 'SUPABASE_SECRET_KEY', 'API_ACCESS_TOKEN']) {
    if (!env[name]?.trim() || /REPLACE|YOUR_PROJECT/.test(env[name])) {
      throw new Error(`Set ${name} in .env before starting the server.`);
    }
  }
  const url = new URL(env.SUPABASE_URL);
  if (!['http:', 'https:'].includes(url.protocol)) {
    throw new Error('SUPABASE_URL must be an HTTP(S) URL.');
  }
  if (env.API_ACCESS_TOKEN.length < 32) {
    throw new Error('API_ACCESS_TOKEN must be at least 32 characters.');
  }
  const port = Number(env.PORT ?? 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('PORT must be an integer between 1 and 65535.');
  }
  return {
    port,
    supabaseUrl: env.SUPABASE_URL,
    supabaseKey: env.SUPABASE_SECRET_KEY,
    apiToken: env.API_ACCESS_TOKEN,
  };
}
