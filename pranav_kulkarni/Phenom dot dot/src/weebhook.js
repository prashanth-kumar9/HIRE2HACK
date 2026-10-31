import { config } from './config.js';

/** Delivers one event to the application server. Throws on failure so it is retried. */
export async function sendToApplicationServer(event) {
  if (!config.applicationServerUrl) {
    console.log('[webhook] APPLICATION_SERVER_URL not set, would send:', event.event_type, event.payload);
    return;
  }
  const res = await fetch(`${config.applicationServerUrl}/webhooks/bookings`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-event-id': String(event.id), // lets the receiver de-duplicate retries
      ...(config.webhookSecret && { 'x-webhook-secret': config.webhookSecret }),
    },
    body: event.payload,
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`application server responded ${res.status}`);
}

let flushing = false;
export async function flushOutbox(service, send = sendToApplicationServer) {
  if (flushing) return 0;
  flushing = true;
  let sent = 0;
  try {
    for (const ev of service.dueEvents()) {
      try { await send(ev); service.eventSent(ev.id); sent++; }
      catch (e) {
        console.error(`[webhook] event ${ev.id} failed:`, e.message);
        service.eventFailed(ev.id, e.message, ev.attempts, config.maxWebhookAttempts);
      }
    }
  } finally { flushing = false; }
  return sent;
}