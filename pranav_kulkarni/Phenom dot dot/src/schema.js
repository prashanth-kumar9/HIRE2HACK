import { z } from 'zod';

const isoDate = z.iso.datetime({ offset: true }).transform((s) => new Date(s).toISOString());
const future = (v) => v.slotStart > new Date().toISOString();
const slot = { slotStart: isoDate, slotEnd: isoDate };
const slotOrder = [(v) => v.slotEnd > v.slotStart, { message: 'slotEnd must be after slotStart', path: ['slotEnd'] }];
const inFuture = [future, { message: 'slotStart must be in the future', path: ['slotStart'] }];

const name = z.string().trim().min(1).max(200);
const email = z.email().toLowerCase();
const phone = z.string().trim().max(30);

export const createBookingSchema = z.object({
  candidateName: name, candidateEmail: email, candidatePhone: phone.optional(), ...slot,
  applicationRef: z.string().max(100).optional(),
  jobId: z.string().max(100).optional(),
  interviewerId: z.string().max(100).optional(),
}).refine(...slotOrder).refine(...inFuture);

export const rescheduleSchema = z.object(slot).refine(...slotOrder).refine(...inFuture);

const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/);
export const createLinkSchema = z.object({
  applicationRef: z.string().max(100).optional(),
  jobId: z.string().max(100).optional(),
  interviewerId: z.string().max(100).optional(),
  candidate: z.object({ name: name.optional(), email: email.optional(), phone: phone.optional() }).optional(),
  expiresAt: isoDate.optional(),
  // Provide either explicit slots or an availability window to generate them from.
  slots: z.array(z.object(slot).refine(...slotOrder)).min(1).max(2000).optional(),
  availability: z.object({
    from: isoDate, to: isoDate,
    slotMinutes: z.number().int().min(5).max(480),
    dailyStart: hhmm.optional(), dailyEnd: hhmm.optional(),
    utcOffsetMinutes: z.number().int().min(-720).max(840).optional(),
    skipWeekends: z.boolean().optional(),
  }).optional(),
}).refine((v) => !!v.slots !== !!v.availability, { message: 'Provide exactly one of slots or availability', path: ['slots'] });

export const linkSubmitSchema = z.object({
  slotStart: isoDate,
  candidateName: name.optional(), candidateEmail: email.optional(), candidatePhone: phone.optional(),
});

export const slotPickSchema = z.object({ slotStart: isoDate });

export const listQuerySchema = z.object({
  status: z.enum(['PENDING', 'PROCESSING', 'NOTIFIED', 'FAILED', 'CANCELLED']).optional(),
  email: z.email().toLowerCase().optional(),
  from: isoDate.optional(),
  to: isoDate.optional(),
  limit: z.coerce.number().int().min(1).max(200).default(50),
  offset: z.coerce.number().int().min(0).default(0),
});