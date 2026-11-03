import { HttpError } from './errors.js';

const MAX_SLOTS = 2000;
const MIN = 60_000, DAY = 86_400_000;

/**
 * Generates slots from an availability window.
 * Working hours are interpreted in the given UTC offset (default UTC).
 */
export function generateSlots({ from, to, slotMinutes, dailyStart = '09:00', dailyEnd = '18:00', utcOffsetMinutes = 0, skipWeekends = true }) {
  const [sh, sm] = dailyStart.split(':').map(Number);
  const [eh, em] = dailyEnd.split(':').map(Number);
  const startMin = sh * 60 + sm, endMin = eh * 60 + em;
  if (endMin <= startMin) throw new HttpError(400, 'dailyEnd must be after dailyStart');
  const fromMs = Date.parse(from), toMs = Date.parse(to);
  if (!(toMs > fromMs)) throw new HttpError(400, 'availability.to must be after availability.from');

  const offMs = utcOffsetMinutes * MIN;
  const out = [];
  // Walk local days that overlap the window.
  for (let dayLocal = Math.floor((fromMs + offMs) / DAY) * DAY; dayLocal <= toMs + offMs; dayLocal += DAY) {
    const dow = new Date(dayLocal).getUTCDay();
    if (skipWeekends && (dow === 0 || dow === 6)) continue;
    for (let m = startMin; m + slotMinutes <= endMin; m += slotMinutes) {
      const s = dayLocal + m * MIN - offMs;
      const e = s + slotMinutes * MIN;
      if (s >= fromMs && e <= toMs) out.push({ slotStart: new Date(s).toISOString(), slotEnd: new Date(e).toISOString() });
      if (out.length > MAX_SLOTS) throw new HttpError(400, `Too many slots (max ${MAX_SLOTS})`);
    }
  }
  if (!out.length) throw new HttpError(400, 'Availability produced no slots');
  return out;
}