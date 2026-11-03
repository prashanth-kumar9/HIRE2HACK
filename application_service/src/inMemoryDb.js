import { createClient } from '@supabase/supabase-js';

const SEED_JOBS = [
  { jobId: '11111111-1111-4111-8111-111111111111', description: 'Node.js Backend Developer' },
  { jobId: '22222222-2222-4222-8222-222222222222', description: 'Frontend Developer' },
];

const SEED_CANDIDATES = [
  { candidateId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', name: 'Alex Kumar', email: 'alex@example.com', phone: '+12025550101', smsConsent: true, emailConsent: true },
  { candidateId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', name: 'Sam Rao', email: 'sam@example.com', phone: '+12025550102', smsConsent: false, emailConsent: true },
];

const SEED_APPLICATIONS = [
  {
    applicationId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    candidateId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    jobId: '11111111-1111-4111-8111-111111111111',
    candidateStatus: 'NEW',
    applicationStatus: 'Applied',
    bookingDateTime: '2026-11-05T10:00:00+05:30',
  },
  {
    applicationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    candidateId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    jobId: '11111111-1111-4111-8111-111111111111',
    candidateStatus: 'NEW',
    applicationStatus: 'Applied',
  },
  {
    applicationId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    candidateId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    jobId: '22222222-2222-4222-8222-222222222222',
    candidateStatus: 'NEW',
    applicationStatus: 'Applied',
  },
];

export function createInMemorySupabase() {
  const jobs = JSON.parse(JSON.stringify(SEED_JOBS));
  const candidates = JSON.parse(JSON.stringify(SEED_CANDIDATES));
  const applications = JSON.parse(JSON.stringify(SEED_APPLICATIONS));

  return createClient('https://inmemory.supabase.local', 'in-memory-anon-key', {
    auth: { persistSession: false, autoRefreshToken: false },
    global: {
      fetch: async (input, init = {}) => {
        const url = new URL(input);
        const path = url.pathname;
        const method = (init.method || 'GET').toUpperCase();

        if (path.endsWith('/Job')) {
          const eqJobId = url.searchParams.get('jobId')?.replace(/^eq\./, '');
          let filtered = [...jobs];
          if (eqJobId) filtered = filtered.filter((j) => j.jobId === eqJobId);

          const offset = Number(url.searchParams.get('offset') ?? 0);
          const limit = Number(url.searchParams.get('limit') ?? 50);
          const paged = filtered.slice(offset, offset + limit);

          return Response.json(paged, {
            headers: { 'content-range': `0-${Math.max(0, filtered.length - 1)}/${filtered.length}` },
          });
        }

        if (path.endsWith('/Application')) {
          const eqJobId = url.searchParams.get('jobId')?.replace(/^eq\./, '');
          const eqAppId = url.searchParams.get('applicationId')?.replace(/^eq\./, '');

          let matching = [...applications];
          if (eqJobId) matching = matching.filter((a) => a.jobId === eqJobId);
          if (eqAppId) matching = matching.filter((a) => a.applicationId === eqAppId);

          if (method === 'PATCH') {
            const body = init.body ? JSON.parse(init.body) : {};
            matching.forEach((app) => Object.assign(app, body));
            return new Response(null, {
              status: 204,
              headers: { 'content-range': `*/${matching.length}` },
            });
          }

          const offset = Number(url.searchParams.get('offset') ?? 0);
          const limit = Number(url.searchParams.get('limit') ?? 5000);
          const withCandidates = matching.map((app) => {
            const cand = candidates.find((c) => c.candidateId === app.candidateId);
            return {
              ...app,
              candidate: cand || null,
            };
          });

          const paged = withCandidates.slice(offset, offset + limit);
          return Response.json(paged, {
            headers: { 'content-range': `0-${Math.max(0, matching.length - 1)}/${matching.length}` },
          });
        }

        return Response.json([], { headers: { 'content-range': '0-0/0' } });
      },
    },
  });
}
