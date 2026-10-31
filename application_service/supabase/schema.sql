-- Run once in a new Supabase project's SQL Editor.
-- Quoted identifiers preserve the exact names in the requested data model.
begin;

create table public."Job" (
  "jobId" uuid primary key default gen_random_uuid(),
  description text not null check (length(trim(description)) > 0)
);

create table public."Candidate" (
  "candidateId" uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  email text not null unique,
  phone text,
  "smsConsent" boolean not null default false,
  "emailConsent" boolean not null default false
);

create table public."Application" (
  "applicationId" uuid primary key default gen_random_uuid(),
  "candidateId" uuid not null references public."Candidate" ("candidateId"),
  "jobId" uuid not null references public."Job" ("jobId"),
  "candidateStatus" text not null default 'NEW',
  "applicationStatus" text not null default 'Applied',
  unique ("candidateId", "jobId"),
  unique ("applicationId", "candidateId")
);

create table public."Booking" (
  "bookingId" uuid primary key default gen_random_uuid(),
  "candidateId" uuid not null references public."Candidate" ("candidateId"),
  "applicationId" uuid not null,
  status text not null default 'Scheduled',
  "bookingDateTime" timestamptz not null,
  -- A booking must belong to the same candidate as its application.
  foreign key ("applicationId", "candidateId")
    references public."Application" ("applicationId", "candidateId")
);

create index application_job_id_idx on public."Application" ("jobId", "applicationId");
create index booking_application_candidate_idx on public."Booking" ("applicationId", "candidateId");
create index booking_candidate_id_idx on public."Booking" ("candidateId");

alter table public."Job" enable row level security;
alter table public."Candidate" enable row level security;
alter table public."Application" enable row level security;
alter table public."Booking" enable row level security;

-- This service uses a server-only secret key (service_role), which bypasses RLS.
-- Browser/public keys have no direct access to candidate data.
revoke all on public."Job", public."Candidate", public."Application", public."Booking" from anon, authenticated;
grant select, insert, update, delete on public."Job", public."Candidate", public."Application", public."Booking" to service_role;

commit;
