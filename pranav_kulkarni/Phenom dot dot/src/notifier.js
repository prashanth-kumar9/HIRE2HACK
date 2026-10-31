import { config } from './config.js';

/** Calls the notification-service. If no URL is configured, logs instead (dev mode). */
export async function notifyInterview(booking) {
  const payload = {
    type: 'INTERVIEW_REMINDER',
    bookingId: booking.id,
    applicationId: booking.application_id,
    to: { name: booking.candidate_name, email: booking.candidate_email, phone: booking.candidate_phone },
    slotStart: booking.slot_start,
    slotEnd: booking.slot_end,
  };
  if (!config.notificationServiceUrl) {
    console.log('[notifier] NOTIFICATION_SERVICE_URL not set, would send:', JSON.stringify(payload));
    return;
  }
  const res = await fetch(`${config.notificationServiceUrl}/notifications`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`notification-service responded ${res.status}`);
}