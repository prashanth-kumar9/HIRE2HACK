import express from 'express';
import { ZodError } from 'zod';
import { HttpError } from './errors.js';
import * as s from './schemas.js';
import { rescheduleUrl } from './service.js';
import { bookPage, reschedulePage } from './pages.js';

const wrap = (fn) => (req, res, next) => { try { fn(req, res); } catch (e) { next(e); } };

export function createApp(service) {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '100kb' }));

  app.get('/health', (_req, res) => res.json({ ok: true }));

  // ---- candidate-facing pages (opened from SMS / email) ----
  const html = (body) => (_req, res) => res.set({ 'cache-control': 'no-store', 'referrer-policy': 'no-referrer' }).type('html').send(body);
  app.get('/book/:token', html(bookPage));
  app.get('/reschedule/:token', html(reschedulePage));

  // ---- application server: request a link ----
  // Returns { token, url, ... }; the application server sends `url` to the candidate.
  app.post('/booking-links', wrap((req, res) => {
    res.status(201).json(service.createLink(s.createLinkSchema.parse(req.body ?? {})));
  }));

  // ---- public form: available slots + submit ----
  app.get('/booking-link/:token', wrap((req, res) => res.set('cache-control', 'no-store').json(service.getLinkView(req.params.token))));
  app.post('/booking-link/:token', wrap((req, res) => {
    res.status(201).json(service.submitViaLink(req.params.token, s.linkSubmitSchema.parse(req.body)));
  }));

  // ---- public reschedule via secret link ----
  app.get('/reschedule-link/:token', wrap((req, res) => res.set('cache-control', 'no-store').json(service.getRescheduleView(req.params.token))));
  app.post('/reschedule-link/:token', wrap((req, res) => {
    const b = service.rescheduleViaLink(req.params.token, s.slotPickSchema.parse(req.body));
    res.json({ slot_start: b.slot_start, slot_end: b.slot_end, rescheduleUrl: rescheduleUrl(req.params.token) });
  }));

  // ---- internal booking API ----
  app.post('/bookings', wrap((req, res) => {
    res.status(201).json(service.createBooking(s.createBookingSchema.parse(req.body)));
  }));
  app.get('/bookings', wrap((req, res) => res.json(service.listBookings(s.listQuerySchema.parse(req.query)))));
  app.get('/bookings/:id', wrap((req, res) => res.json(service.getBooking(req.params.id))));
  app.patch('/bookings/:id', wrap((req, res) => {
    res.json(service.reschedule(req.params.id, s.rescheduleSchema.parse(req.body)));
  }));
  app.delete('/bookings/:id', wrap((req, res) => res.json(service.cancel(req.params.id))));

  app.use((_req, res) => res.status(404).json({ error: 'Not found' }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    if (err instanceof ZodError) {
      return res.status(400).json({ error: 'Validation failed', details: err.issues.map((i) => ({ path: i.path.join('.'), message: i.message })) });
    }
    if (err instanceof HttpError) return res.status(err.status).json({ error: err.message });
    if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Invalid JSON' });
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  });

  return app;
}