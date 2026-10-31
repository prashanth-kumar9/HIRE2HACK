import { test } from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db.js';
import { createService } from '../src/service.js';
import { createApp } from '../src/app.js';
import { processDueBookings } from '../src/cron.js';
import { flushOutbox } from '../src/webhook.js';

async function setup() {
  const service = createService(openDb(':memory:'));
  const server = createApp(service).listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, path, body) => {
    const r = await fetch(base + path, { method, headers: { 'content-type': 'application/json' }, body: body && JSON.stringify(body) });
    return { status: r.status, body: await r.json() };
  };
  const events = [];
  const drain = async () => { await flushOutbox(service, async (e) => events.push(JSON.parse(e.payload))); return events.splice(0); };
  return { service, call, drain, close: () => { server.close(); server.closeAllConnections(); } };
}
const slot = (minsFromNow, len = 30) => ({
  slotStart: new Date(Date.now() + minsFromNow * 60000).toISOString(),
  slotEnd: new Date(Date.now() + (minsFromNow + len) * 60000).toISOString(),
});
const cand = { candidateName: 'Asha', candidateEmail: 'asha@example.com' };
const tokenOf = (url) => url.split('/').pop();

test('internal API: create, get, list, reschedule, conflict, events', async () => {
  const { call, drain, close } = await setup();
  const s120 = slot(120);
  const a = await call('POST', '/bookings', { ...cand, ...s120 });
  assert.equal(a.status, 201);
  assert.equal((await call('POST', '/bookings', { ...cand, ...s120 })).status, 409);
  assert.equal((await call('GET', `/bookings/${a.body.id}`)).body.id, a.body.id);
  assert.equal((await call('GET', '/bookings?status=PENDING')).body.length, 1);
  const p = await call('PATCH', `/bookings/${a.body.id}`, slot(240));
  assert.equal(p.status, 200);
  assert.ok(p.body.slot_start > a.body.slot_start);
  assert.equal((await call('POST', '/bookings', { ...cand, candidateEmail: 'bad' })).status, 400);
  assert.deepEqual((await drain()).map((e) => e.event), ['BOOKING_CREATED', 'BOOKING_RESCHEDULED']);
  close();
});

test('full flow: link -> slots -> book -> reschedule link, app server informed', async () => {
  const { call, drain, close } = await setup();
  const from = new Date(Date.now() + 3600e3).toISOString();
  const to = new Date(Date.now() + 3 * 86400e3).toISOString();
  const created = await call('POST', '/booking-links', {
    applicationRef: 'APP-1', jobId: 'J1', candidate: { name: 'Asha', email: 'asha@example.com' },
    availability: { from, to, slotMinutes: 30, dailyStart: '00:00', dailyEnd: '23:30', skipWeekends: false },
  });
  assert.equal(created.status, 201);
  assert.match(created.body.url, /\/book\/.+/);
  const token = created.body.token;

  const view = await call('GET', `/booking-link/${token}`);
  assert.equal(view.body.status, 'OPEN');
  assert.ok(view.body.slots.length > 10);
  const [first, second] = view.body.slots;

  // book first slot (candidate details come from the link prefill)
  const booked = await call('POST', `/booking-link/${token}`, { slotStart: first.slotStart });
  assert.equal(booked.status, 201);
  assert.equal(booked.body.booking.slot_end, first.slotEnd);
  assert.match(booked.body.rescheduleUrl, /\/reschedule\/.+/);
  // link is single-use, slot no longer offered
  assert.equal((await call('POST', `/booking-link/${token}`, { slotStart: second.slotStart })).status, 409);
  assert.equal((await call('GET', `/booking-link/${token}`)).body.status, 'BOOKED');
  // slot outside the link is refused
  const other = await call('POST', '/booking-links', { slots: [slot(500)] });
  assert.equal((await call('POST', `/booking-link/${other.body.token}`, { ...cand, slotStart: slot(999).slotStart })).status, 409);

  let ev = await drain();
  assert.equal(ev.length, 1);
  assert.equal(ev[0].event, 'BOOKING_CREATED');
  assert.equal(ev[0].applicationRef, 'APP-1');
  assert.equal(ev[0].rescheduleUrl, booked.body.rescheduleUrl);

  // reschedule through the secret link
  const rt = tokenOf(booked.body.rescheduleUrl);
  const rv = await call('GET', `/reschedule-link/${rt}`);
  assert.equal(rv.body.current.slotStart, first.slotStart);
  assert.ok(rv.body.slots.some((s) => s.slotStart === second.slotStart));
  assert.ok(!rv.body.slots.some((s) => s.slotStart === first.slotStart));
  const moved = await call('POST', `/reschedule-link/${rt}`, { slotStart: second.slotStart });
  assert.equal(moved.status, 200);
  ev = await drain();
  assert.equal(ev[0].event, 'BOOKING_RESCHEDULED');
  assert.equal(ev[0].slotStart, second.slotStart);
  assert.equal(ev[0].previousSlotStart, first.slotStart);
  assert.equal((await call('POST', `/reschedule-link/${rt}`, { slotStart: second.slotStart })).status, 409);
  assert.equal((await call('GET', '/reschedule-link/nope')).status, 404);
  close();
});

test('failed webhook is retried, not lost', async () => {
  const { service, call, close } = await setup();
  await call('POST', '/bookings', { ...cand, ...slot(300) });
  assert.equal(await flushOutbox(service, async () => { throw new Error('down'); }), 0);
  assert.equal(service.dueEvents().length, 0); // backed off
  close();
});

test('cron picks due bookings, creates application, notifies', async () => {
  const { service, call, close } = await setup();
  const due = await call('POST', '/bookings', { ...cand, ...slot(30) });
  const later = await call('POST', '/bookings', { ...cand, candidateEmail: 'b@example.com', ...slot(600) });
  const sent = [];
  assert.equal(await processDueBookings(service, { notify: async (b) => sent.push(b) }), 1);
  const after = (await call('GET', `/bookings/${due.body.id}`)).body;
  assert.equal(after.status, 'NOTIFIED');
  assert.ok(after.application_id);
  assert.equal((await call('GET', `/bookings/${later.body.id}`)).body.status, 'PENDING');
  assert.equal(sent.length, 1);
  close();
});

test('cron retries then fails after max attempts', async () => {
  const { service, call, close } = await setup();
  const b = await call('POST', '/bookings', { ...cand, ...slot(10) });
  const boom = async () => { throw new Error('down'); };
  for (let i = 0; i < 3; i++) await processDueBookings(service, { notify: boom });
  const after = (await call('GET', `/bookings/${b.body.id}`)).body;
  assert.equal(after.status, 'FAILED');
  assert.equal(after.attempts, 3);
  close();
});