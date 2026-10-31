import { config } from './config.js';
import { openDb } from './db.js';
import { createService } from './service.js';
import { createApp } from './app.js';
import { startCron } from './cron.js';
import { flushOutbox } from './webhook.js';

const db = openDb(config.dbPath);
// New events are pushed to the application server right after commit; cron retries failures.
const service = createService(db, { onOutbox: () => flushOutbox(service).catch((e) => console.error('[webhook]', e)) });
const app = createApp(service);

const task = config.cronEnabled ? startCron(service) : null;
const server = app.listen(config.port, () => console.log(`booking-service listening on :${config.port}`));

const shutdown = () => { task?.stop(); server.close(() => { db.close(); process.exit(0); }); };
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);