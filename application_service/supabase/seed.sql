-- Optional sample data. Safe to rerun; existing rows are preserved.
begin;
insert into public."Job" ("jobId", description) values
  ('11111111-1111-4111-8111-111111111111', 'Node.js Backend Developer'),
  ('22222222-2222-4222-8222-222222222222', 'Frontend Developer')
on conflict do nothing;

insert into public."Candidate" ("candidateId", name, email, phone, "smsConsent", "emailConsent") values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Alex Kumar', 'alex@example.com', '+12025550101', true, true),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Sam Rao', 'sam@example.com', '+12025550102', false, true)
on conflict do nothing;

insert into public."Application" ("applicationId", "candidateId", "jobId") values
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111'),
  ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', '11111111-1111-4111-8111-111111111111'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '22222222-2222-4222-8222-222222222222')
on conflict do nothing;

insert into public."Booking" ("bookingId", "candidateId", "applicationId", "bookingDateTime") values
  ('ffffffff-ffff-4fff-8fff-ffffffffffff', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', '2026-11-05T10:00:00+05:30')
on conflict do nothing;
commit;
