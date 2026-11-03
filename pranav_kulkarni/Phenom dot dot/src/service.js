import { randomUUID, randomBytes } from 'node:crypto';
import { HttpError } from './errors.js';
import { config } from './config.js';
import { generateSlots } from './slots.js';

const now = () => new Date().toISOString();
const ACTIVE = "('PENDING','PROCESSING','NOTIFIED')";
const newToken = () => randomBytes(18).toString('base64url');

export const bookingUrl = (token) => `${config.baseUrl}/book/${token}`;
export const rescheduleUrl = (token) => (token ? `${config.baseUrl}/reschedule/${token}` : null);

export function createService(db, { onOutbox } = {}) {
  const q = {
    insertLink: db.prepare(`INSERT INTO booking_links
      (token, application_ref, job_id, interviewer_id, candidate_name, candidate_email, candidate_phone, expires_at, created_at)
      VALUES (?,?,?,?,?,?,?,?,?)`),
    insertSlot: db.prepare('INSERT OR IGNORE INTO link_slots (link_token, slot_start, slot_end) VALUES (?,?,?)'),
    getLink: db.prepare('SELECT * FROM booking_links WHERE token = ?'),
    linkSlot: db.prepare('SELECT * FROM link_slots WHERE link_token = ? AND slot_start = ?'),
    useLink: db.prepare('UPDATE booking_links SET booking_id = ? WHERE token = ? AND booking_id IS NULL'),
    availableSlots: db.prepare(`SELECT slot_start, slot_end FROM link_slots ls
      WHERE link_token = ? AND slot_start > ?
        AND NOT EXISTS (SELECT 1 FROM bookings b
          WHERE b.slot_start = ls.slot_start AND COALESCE(b.interviewer_id,'') = COALESCE(?, '') AND b.status IN ${ACTIVE})
      ORDER BY slot_start`),
    insertApp: db.prepare(`INSERT INTO applications
      (id, application_ref, candidate_name, candidate_email, candidate_phone, job_id, booking_link_token, created_at) VALUES (?,?,?,?,?,?,?,?)`),
    getBooking: db.prepare('SELECT * FROM bookings WHERE id = ?'),
    getByResched: db.prepare('SELECT * FROM bookings WHERE reschedule_token = ?'),
    insertBooking: db.prepare(`INSERT INTO bookings
      (id, application_id, application_ref, link_token, reschedule_token, candidate_name, candidate_email, candidate_phone,
       job_id, interviewer_id, slot_start, slot_end, status, created_at, updated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)`),
    move: db.prepare(`UPDATE bookings SET slot_start=?, slot_end=?, status='PENDING', attempts=0, last_error=NULL, notified_at=NULL, updated_at=? WHERE id=?`),
    due: db.prepare(`SELECT id FROM bookings WHERE status = 'PENDING' AND slot_start <= ? AND slot_end > ? ORDER BY slot_start`),
    claim: db.prepare(`UPDATE bookings SET status='PROCESSING', attempts = attempts + 1, updated_at=? WHERE id=? AND status='PENDING'`),
    markNotified: db.prepare(`UPDATE bookings SET status='NOTIFIED', notified_at=?, last_error=NULL, updated_at=? WHERE id=?`),
    markRetry: db.prepare(`UPDATE bookings SET status=?, last_error=?, updated_at=? WHERE id=?`),
    expireStale: db.prepare(`UPDATE bookings SET status='FAILED', last_error='slot passed before notification', updated_at=? WHERE status='PENDING' AND slot_end <= ?`),
    setApp: db.prepare('UPDATE bookings SET application_id=?, updated_at=? WHERE id=?'),
    outInsert: db.prepare('INSERT INTO outbox (event_type, payload, next_attempt_at, created_at) VALUES (?,?,?,?)'),
    outDue: db.prepare(`SELECT * FROM outbox WHERE status='PENDING' AND next_attempt_at <= ? ORDER BY id LIMIT ?`),
    outSent: db.prepare(`UPDATE outbox SET status='SENT', attempts=attempts+1, last_error=NULL WHERE id=?`),
    outFail: db.prepare(`UPDATE outbox SET status=?, attempts=attempts+1, last_error=?, next_attempt_at=? WHERE id=?`),
  };

  let dirty = false;
  const tx = (fn) => {
    db.exec('BEGIN IMMEDIATE');
    let r;
    try { r = fn(); db.exec('COMMIT'); }
    catch (e) { db.exec('ROLLBACK'); dirty = false; throw e; }
    if (dirty) { dirty = false; queueMicrotask(() => onOutbox?.()); }
    return r;
  };

  const isSlotConflict = (e) => /UNIQUE constraint failed/.test(e.message);

  function emit(type, booking, extra = {}) {
    const payload = {
      event: type,
      occurredAt: now(),
      bookingId: booking.id,
      applicationId: booking.application_id,
      applicationRef: booking.application_ref,
      jobId: booking.job_id,
      interviewerId: booking.interviewer_id,
      candidate: { name: booking.candidate_name, email: booking.candidate_email, phone: booking.candidate_phone },
      slotStart: booking.slot_start,
      slotEnd: booking.slot_end,
      status: booking.status,
      rescheduleUrl: rescheduleUrl(booking.reschedule_token),
      ...extra,
    };
    q.outInsert.run(type, JSON.stringify(payload), now(), now());
    dirty = true;
  }

  function insertApplication(c) {
    const id = randomUUID();
    q.insertApp.run(id, c.applicationRef ?? null, c.candidateName, c.candidateEmail, c.candidatePhone ?? null, c.jobId ?? null, c.token ?? null, now());
    return id;
  }

  function insertBooking(c, applicationId) {
    const id = randomUUID();
    const t = now();
    try {
      q.insertBooking.run(id, applicationId, c.applicationRef ?? null, c.token ?? null, c.token ? newToken() : null,
        c.candidateName, c.candidateEmail, c.candidatePhone ?? null, c.jobId ?? null, c.interviewerId ?? null,
        c.slotStart, c.slotEnd, t, t);
    } catch (e) {
      if (isSlotConflict(e)) throw new HttpError(409, 'Slot is already booked');
      throw e;
    }
    return q.getBooking.get(id);
  }

  function loadLink(token) {
    let link = q.getLink.get(token);
    if (!link && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(token)) {
      try {
        const from = new Date(Date.now() + 3600e3 * 2).toISOString();
        const to = new Date(Date.now() + 86400e3 * 4).toISOString();
        const slots = generateSlots({ from, to, slotMinutes: 30, dailyStart: '09:00', dailyEnd: '18:00', skipWeekends: true });
        q.insertLink.run(token, token, null, null, 'Candidate', null, null, new Date(Date.now() + 86400e3 * 7).toISOString(), now());
        for (const s of slots) q.insertSlot.run(token, s.slotStart, s.slotEnd);
        link = q.getLink.get(token);
      } catch (_) {}
    }
    if (!link) throw new HttpError(404, 'Booking link not found');
    if (link.expires_at && link.expires_at < now()) throw new HttpError(410, 'Booking link expired');
    return link;
  }

  function loadReschedulable(token) {
    const b = q.getByResched.get(token);
    if (!b) throw new HttpError(404, 'Reschedule link not found');
    if (b.status === 'CANCELLED') throw new HttpError(410, 'Booking was cancelled');
    if (b.status === 'FAILED' || b.status === 'PROCESSING' || b.slot_start <= now()) {
      throw new HttpError(409, 'Booking can no longer be rescheduled');
    }
    return b;
  }

  function moveBooking(b, slotStart, slotEnd, extra) {
    const previousSlotStart = b.slot_start, previousSlotEnd = b.slot_end;
    try { q.move.run(slotStart, slotEnd, now(), b.id); }
    catch (e) {
      if (isSlotConflict(e)) throw new HttpError(409, 'Slot is already booked');
      throw e;
    }
    const updated = q.getBooking.get(b.id);
    emit('BOOKING_RESCHEDULED', updated, { previousSlotStart, previousSlotEnd, ...extra });
    return updated;
  }

  const publicSlots = (link) => q.availableSlots.all(link.token, now(), link.interviewer_id)
    .map((s) => ({ slotStart: s.slot_start, slotEnd: s.slot_end }));

  return {
    // POST /booking-links — application server asks for a link to send to the candidate.
    createLink({ applicationRef, jobId, interviewerId, candidate, expiresAt, slots, availability }) {
      const list = slots ?? generateSlots(availability);
      return tx(() => {
        const token = newToken();
        q.insertLink.run(token, applicationRef ?? null, jobId ?? null, interviewerId ?? null,
          candidate?.name ?? null, candidate?.email ?? null, candidate?.phone ?? null, expiresAt ?? null, now());
        for (const s of list) q.insertSlot.run(token, s.slotStart, s.slotEnd);
        return { token, url: bookingUrl(token), expiresAt: expiresAt ?? null, slotCount: list.length };
      });
    },

    // GET /booking-link/:token — drives the candidate form.
    getLinkView(token) {
      const link = loadLink(token);
      const base = {
        candidate: { name: link.candidate_name, email: link.candidate_email, phone: link.candidate_phone },
      };
      if (link.booking_id) {
        const b = q.getBooking.get(link.booking_id);
        return { ...base, status: 'BOOKED', booking: { slotStart: b.slot_start, slotEnd: b.slot_end, status: b.status }, rescheduleUrl: rescheduleUrl(b.reschedule_token) };
      }
      return { ...base, status: 'OPEN', slots: publicSlots(link) };
    },

    // POST /booking-link/:token — candidate picks a slot; creates Application + Booking, informs app server.
    submitViaLink(token, input) {
      const link = loadLink(token);
      return tx(() => {
        // Re-read inside the transaction so two submissions can't both use the link.
        if (q.getLink.get(token).booking_id) throw new HttpError(409, 'This link has already been used');
        const slot = q.linkSlot.get(token, input.slotStart);
        if (!slot || slot.slot_start <= now()) throw new HttpError(409, 'Slot is not available');
        const c = {
          candidateName: link.candidate_name ?? input.candidateName,
          candidateEmail: link.candidate_email ?? input.candidateEmail,
          candidatePhone: link.candidate_phone ?? input.candidatePhone,
          applicationRef: link.application_ref, jobId: link.job_id, interviewerId: link.interviewer_id,
          token, slotStart: slot.slot_start, slotEnd: slot.slot_end,
        };
        if (!c.candidateName || !c.candidateEmail) throw new HttpError(400, 'candidateName and candidateEmail are required');
        const applicationId = insertApplication(c);
        const booking = insertBooking(c, applicationId);
        q.useLink.run(booking.id, token);
        emit('BOOKING_CREATED', booking);
        return { application: { id: applicationId }, booking, rescheduleUrl: rescheduleUrl(booking.reschedule_token) };
      });
    },

    // GET /reschedule-link/:token
    getRescheduleView(token) {
      const b = loadReschedulable(token);
      const link = q.getLink.get(b.link_token);
      return {
        candidate: { name: b.candidate_name },
        current: { slotStart: b.slot_start, slotEnd: b.slot_end },
        slots: publicSlots(link),
      };
    },

    // POST /reschedule-link/:token — candidate picks another slot; informs app server.
    rescheduleViaLink(token, { slotStart }) {
      return tx(() => {
        const b = loadReschedulable(token);
        const slot = q.linkSlot.get(b.link_token, slotStart);
        if (!slot || slot.slot_start <= now() || slot.slot_start === b.slot_start) throw new HttpError(409, 'Slot is not available');
        return moveBooking(b, slot.slot_start, slot.slot_end, { rescheduledBy: 'candidate' });
      });
    },

    // POST /bookings — booking only (no link); application is created later by the cron.
    createBooking(input) {
      return tx(() => {
        const booking = insertBooking(input, null);
        emit('BOOKING_CREATED', booking);
        return booking;
      });
    },

    listBookings({ status, email, from, to, limit, offset }) {
      const where = [], args = [];
      if (status) { where.push('status = ?'); args.push(status); }
      if (email) { where.push('candidate_email = ?'); args.push(email); }
      if (from) { where.push('slot_start >= ?'); args.push(from); }
      if (to) { where.push('slot_start <= ?'); args.push(to); }
      const w = where.length ? `WHERE ${where.join(' AND ')}` : '';
      return db.prepare(`SELECT * FROM bookings ${w} ORDER BY slot_start LIMIT ? OFFSET ?`).all(...args, limit, offset);
    },

    getBooking(id) {
      const b = q.getBooking.get(id);
      if (!b) throw new HttpError(404, 'Booking not found');
      return { ...b, reschedule_url: rescheduleUrl(b.reschedule_token) };
    },

    // PATCH /bookings/:id — internal reschedule to an arbitrary slot.
    reschedule(id, { slotStart, slotEnd }) {
      const updated = tx(() => {
        const b = q.getBooking.get(id);
        if (!b) throw new HttpError(404, 'Booking not found');
        if (b.status === 'CANCELLED' || b.status === 'PROCESSING' || b.status === 'FAILED') {
          throw new HttpError(409, `Cannot reschedule a booking in status ${b.status}`);
        }
        return moveBooking(b, slotStart, slotEnd, { rescheduledBy: 'system' });
      });
      return this.getBooking(updated.id);
    },

    cancel(id) {
      return tx(() => {
        const b = q.getBooking.get(id);
        if (!b) throw new HttpError(404, 'Booking not found');
        if (b.status === 'CANCELLED') return b;
        db.prepare(`UPDATE bookings SET status='CANCELLED', updated_at=? WHERE id=?`).run(now(), id);
        const updated = q.getBooking.get(id);
        emit('BOOKING_CANCELLED', updated);
        return updated;
      });
    },

    // ---- reminder cron support ----
    claimDueBookings(windowMinutes) {
      const t = new Date();
      const horizon = new Date(t.getTime() + windowMinutes * 60_000).toISOString();
      const iso = t.toISOString();
      return tx(() => {
        q.expireStale.run(iso, iso);
        const claimed = [];
        for (const { id } of q.due.all(horizon, iso)) {
          if (q.claim.run(iso, id).changes === 1) claimed.push(q.getBooking.get(id));
        }
        return claimed;
      });
    },

    ensureApplication(booking) {
      if (booking.application_id) return booking.application_id;
      return tx(() => {
        const id = insertApplication({
          applicationRef: booking.application_ref,
          candidateName: booking.candidate_name, candidateEmail: booking.candidate_email,
          candidatePhone: booking.candidate_phone, jobId: booking.job_id,
        });
        q.setApp.run(id, now(), booking.id);
        return id;
      });
    },

    markNotified(id) { const t = now(); q.markNotified.run(t, t, id); },
    markFailure(id, error, attempts, maxAttempts) {
      q.markRetry.run(attempts >= maxAttempts ? 'FAILED' : 'PENDING', String(error).slice(0, 500), now(), id);
    },

    // ---- outbox (application-server webhooks) ----
    dueEvents(limit = 50) { return q.outDue.all(now(), limit); },
    eventSent(id) { q.outSent.run(id); },
    eventFailed(id, error, attempts, maxAttempts) {
      const attempt = attempts + 1;
      const backoffMs = Math.min(2 ** attempt, 60) * 60_000 / 4; // 30s, 1m, 2m ... capped at 15m
      q.outFail.run(attempt >= maxAttempts ? 'FAILED' : 'PENDING', String(error).slice(0, 500),
        new Date(Date.now() + backoffMs).toISOString(), id);
    },
  };
}