const port = Number(process.env.PORT || 3001);
export const config = {
  port,
  // Public base URL used when building candidate-facing links (SMS / email).
  baseUrl: (process.env.BASE_URL || `http://localhost:${port}`).replace(/\/$/, ''),
  dbPath: process.env.DB_PATH || './data/bookings.db',
  // Bookings starting within this many minutes are picked up by the cron job.
  notifyWindowMinutes: Number(process.env.NOTIFY_WINDOW_MINUTES || 60),
  cronSchedule: process.env.CRON_SCHEDULE || '* * * * *',
  cronEnabled: process.env.CRON_ENABLED !== 'false',
  // notification-service (SMS/email reminders)
  notificationServiceUrl: process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:8081',
  maxNotifyAttempts: Number(process.env.MAX_NOTIFY_ATTEMPTS || 3),
  // application server (receives booking events)
  applicationServerUrl: (process.env.APPLICATION_SERVER_URL || 'http://localhost:3002').replace(/\/$/, ''),
  webhookSecret: process.env.WEBHOOK_SECRET || '',
  maxWebhookAttempts: Number(process.env.MAX_WEBHOOK_ATTEMPTS || 8),
};
