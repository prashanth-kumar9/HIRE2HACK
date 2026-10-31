import cron from 'node-cron';
import { config } from './config.js';
import { notifyInterview } from './notifier.js';
import { flushOutbox } from './webhook.js';

/** One tick: claim due bookings, ensure an Application exists, notify, update status. */
export async function processDueBookings(service, { notify = notifyInterview } = {}) {
  const claimed = service.claimDueBookings(config.notifyWindowMinutes);
  for (const booking of claimed) {
    try {
      const applicationId = service.ensureApplication(booking);
      await notify({ ...booking, application_id: applicationId });
      service.markNotified(booking.id);
    } catch (err) {
      console.error(`[cron] booking ${booking.id} failed:`, err.message);
      service.markFailure(booking.id, err.message, booking.attempts, config.maxNotifyAttempts);
    }
  }
  return claimed.length;
}

export function startCron(service) {
  let running = false; // prevent overlapping ticks
  return cron.schedule(config.cronSchedule, async () => {
    if (running) return;
    running = true;
    try {
      await flushOutbox(service); // retry any undelivered application-server events
      const n = await processDueBookings(service);
      if (n) console.log(`[cron] processed ${n} booking(s)`);
    } catch (e) {
      console.error('[cron] tick failed:', e);
    } finally {
      running = false;
    }
  });
}