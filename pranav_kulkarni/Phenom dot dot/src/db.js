import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';

export function openDb(dbPath) {
  if (dbPath !== ':memory:') fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;

    -- A link the application server asks for; candidate opens it from SMS/email.
    CREATE TABLE IF NOT EXISTS booking_links (
      token TEXT PRIMARY KEY,
      application_ref TEXT,          -- application server's own id for the candidate/application
      job_id TEXT,
      interviewer_id TEXT,
      candidate_name TEXT,           -- optional prefill
      candidate_email TEXT,
      candidate_phone TEXT,
      expires_at TEXT,
      booking_id TEXT,               -- set once the candidate has booked (link is single-use)
      created_at TEXT NOT NULL
    );

    -- Slots offered by a link.
    CREATE TABLE IF NOT EXISTS link_slots (
      link_token TEXT NOT NULL REFERENCES booking_links(token) ON DELETE CASCADE,
      slot_start TEXT NOT NULL,
      slot_end TEXT NOT NULL,
      PRIMARY KEY (link_token, slot_start)
    );

    CREATE TABLE IF NOT EXISTS applications (
      id TEXT PRIMARY KEY,
      application_ref TEXT,
      candidate_name TEXT NOT NULL,
      candidate_email TEXT NOT NULL,
      candidate_phone TEXT,
      job_id TEXT,
      booking_link_token TEXT REFERENCES booking_links(token),
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS bookings (
      id TEXT PRIMARY KEY,
      application_id TEXT REFERENCES applications(id),
      application_ref TEXT,
      link_token TEXT REFERENCES booking_links(token),
      reschedule_token TEXT UNIQUE,
      candidate_name TEXT NOT NULL,
      candidate_email TEXT NOT NULL,
      candidate_phone TEXT,
      job_id TEXT,
      interviewer_id TEXT,
      slot_start TEXT NOT NULL,
      slot_end TEXT NOT NULL,
      -- PENDING -> PROCESSING -> NOTIFIED | FAILED ; CANCELLED
      status TEXT NOT NULL DEFAULT 'PENDING',
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT,
      notified_at TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_bookings_due ON bookings(status, slot_start);
    -- One active booking per interviewer per slot start (NULL interviewer = shared pool).
    CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_slot
      ON bookings(COALESCE(interviewer_id, ''), slot_start)
      WHERE status IN ('PENDING','PROCESSING','NOTIFIED');

    -- Events to deliver to the application server (transactional outbox).
    CREATE TABLE IF NOT EXISTS outbox (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      event_type TEXT NOT NULL,
      payload TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',   -- PENDING | SENT | FAILED
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT,
      next_attempt_at TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_outbox_due ON outbox(status, next_attempt_at);
  `);
  return db;
}