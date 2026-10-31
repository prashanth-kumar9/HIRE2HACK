import { test } from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { createClient } from '@supabase/supabase-js';
import { createApp } from '../src/app.js';
import { readConfig } from '../src/config.js';

const TOKEN = 'test-api-token-with-at-least-32-characters';
const JOB = '11111111-1111-4111-8111-111111111111';
const OTHER_JOB = '22222222-2222-4222-8222-222222222222';

// Exercise the real SDK with a deterministic PostgREST transport, without credentials.
function fixture({ missingJob = false, dbFailure = false, empty = false } = {}) {
  const calls = [];
  const applications = empty ? [] : [
    { applicationId: 'a1', candidateId: 'c1', jobId: JOB, candidateStatus: 'NEW', applicationStatus: 'Applied',
      candidate: { candidateId: 'c1', name: 'Alex', email: 'alex@example.com', smsConsent: true } },
    { applicationId: 'a2', candidateId: 'c2', jobId: JOB, candidateStatus: 'NEW', applicationStatus: 'Applied',
      candidate: { candidateId: 'c2', name: 'Sam', email: 'sam@example.com', smsConsent: false } },
    { applicationId: 'a3', candidateId: 'c1', jobId: OTHER_JOB, candidateStatus: 'NEW', applicationStatus: 'Applied' },
  ];
  const supabase = createClient('https://test.supabase.co', 'test-secret', {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { fetch: async (input, init) => {
      const url = new URL(input);
      calls.push({ url, method: init.method, body: init.body });
      if (dbFailure) return Response.json({ code: '42501', message: 'private database details' }, { status: 403 });
      if (url.pathname.endsWith('/Job')) {
        const data = missingJob ? [] : [{ jobId: JOB, description: 'Backend Developer' }];
        return Response.json(data, { headers: { 'content-range': `0-0/${data.length}` } });
      }
      assert.equal(url.pathname, '/rest/v1/Application');
      const rows = applications.filter(a => `eq.${a.jobId}` === url.searchParams.get('jobId'));
      if (init.method === 'PATCH') {
        assert.match(new Headers(init.headers).get('prefer'), /count=exact/);
        rows.forEach(row => Object.assign(row, JSON.parse(init.body)));
        return new Response(null, { status: 204, headers: { 'content-range': `*/${rows.length}` } });
      }
      assert.match(url.searchParams.get('select'), /candidate:Candidate\(/);
      const offset = Number(url.searchParams.get('offset') ?? 0);
      const limit = Number(url.searchParams.get('limit') ?? 50);
      return Response.json(rows.slice(offset, offset + limit), {
        headers: { 'content-range': `0-${Math.max(0, rows.length - 1)}/${rows.length}` },
      });
    } },
  });
  return { app: createApp({ supabase, apiToken: TOKEN, logger: { error() {} } }), calls, applications };
}
const authorized = (app, method, path) => request(app)[method](path).set('Authorization', `Bearer ${TOKEN}`);

test('frontend assets are public but data endpoints and private files remain protected', async () => {
  const { app, calls } = fixture();
  const index = await request(app).get('/').expect(200).expect('Content-Type', /html/);
  assert.match(index.text, /screenflow/);
  assert.match(index.headers['content-security-policy'], /connect-src 'self'/);
  assert.ok(!index.text.includes(TOKEN));
  await request(app).get('/app.js').expect(200).expect('Content-Type', /javascript/);
  await request(app).get('/styles.css').expect(200).expect('Content-Type', /css/);
  await request(app).get('/.env').expect(401);
  await authorized(app, 'get', '/.env').expect(404);
  await authorized(app, 'get', '/src/config.js').expect(404);
  assert.equal(calls.length, 0);
});

test('all endpoints require a valid token before database access', async () => {
  const { app, calls } = fixture();
  for (const [method, path] of [['get', '/jobIDs'], ['get', `/Job/${JOB}`], ['post', `/invite/${JOB}`]]) {
    await request(app)[method](path).expect(401);
    await request(app)[method](path).set('Authorization', 'Bearer incorrect').expect(401);
  }
  assert.equal(calls.length, 0);
});
test('GET /jobIDs returns jobs and forwards pagination to Supabase', async () => {
  const { app, calls } = fixture();
  const { body } = await authorized(app, 'get', '/jobIDs?limit=10&offset=2').expect(200);
  assert.equal(body.data[0].jobId, JOB);
  assert.deepEqual(body.pagination, { limit: 10, offset: 2, total: 1 });
  assert.equal(calls[0].url.searchParams.get('limit'), '10');
  assert.equal(calls[0].url.searchParams.get('offset'), '2');
});
test('GET /Job returns scoped applications and candidate details', async () => {
  const { app } = fixture();
  const { body } = await authorized(app, 'get', `/Job/${JOB}`).expect(200);
  assert.equal(body.job.jobId, JOB);
  assert.equal(body.applications.length, 2);
  assert.equal(body.applications[0].candidate.email, 'alex@example.com');
  assert.ok(body.applications.every(a => a.jobId === JOB));
  assert.equal(body.pagination.total, 2);
  const page = await authorized(app, 'get', `/Job/${JOB}?limit=1&offset=1`).expect(200);
  assert.equal(page.body.applications.length, 1);
  assert.equal(page.body.applications[0].applicationId, 'a2');
});
test('invite updates both statuses for all and only the selected job, including repeat calls', async () => {
  const { app, applications } = fixture();
  const { body } = await authorized(app, 'post', `/invite/${JOB}`).expect(200);
  assert.equal(body.updatedCount, 2);
  for (const row of applications.filter(a => a.jobId === JOB)) {
    assert.equal(row.candidateStatus, 'SMS_SENT');
    assert.equal(row.applicationStatus, 'Invited Candidates');
  }
  assert.equal(applications.find(a => a.jobId === OTHER_JOB).candidateStatus, 'NEW');
  const repeated = await authorized(app, 'post', `/invite/${JOB}`).send({}).expect(200);
  assert.equal(repeated.body.updatedCount, 2);
});
test('unknown jobs return 404 without writing', async () => {
  const { app, calls } = fixture({ missingJob: true });
  await authorized(app, 'get', `/Job/${JOB}`).expect(404);
  await authorized(app, 'post', `/invite/${JOB}`).expect(404);
  assert.ok(calls.every(c => c.method === 'GET'));
});
test('jobs with no applicants return empty results and zero updates', async () => {
  const { app } = fixture({ empty: true });
  const list = await authorized(app, 'get', `/Job/${JOB}`).expect(200);
  assert.deepEqual(list.body.applications, []);
  const invite = await authorized(app, 'post', `/invite/${JOB}`).expect(200);
  assert.equal(invite.body.updatedCount, 0);
});
test('invalid IDs and pagination are rejected without database access', async () => {
  const { app, calls } = fixture();
  await authorized(app, 'get', '/Job/not-a-uuid').expect(400);
  await authorized(app, 'post', '/invite/not-a-uuid').expect(400);
  for (const query of ['limit=0', 'limit=101', 'offset=-1', 'offset=1.5', 'limit=2&limit=3', 'offset=9999999999999999']) {
    await authorized(app, 'get', `/jobIDs?${query}`).expect(400);
    await authorized(app, 'get', `/Job/${JOB}?${query}`).expect(400);
  }
  assert.equal(calls.length, 0);
});
test('invite rejects unexpected selectors and invalid JSON', async () => {
  const { app, calls } = fixture();
  await authorized(app, 'post', `/invite/${JOB}`).send({ candidateId: 'c1' }).expect(400);
  await authorized(app, 'post', `/invite/${JOB}`).send([]).expect(400);
  await authorized(app, 'post', `/invite/${JOB}`).set('Content-Type', 'application/json').send('{bad').expect(400);
  assert.equal(calls.length, 0);
});
test('database failures do not leak internal details', async () => {
  const { app } = fixture({ dbFailure: true });
  for (const [method, path] of [['get', '/jobIDs'], ['get', `/Job/${JOB}`], ['post', `/invite/${JOB}`]]) {
    const response = await authorized(app, method, path).expect(500);
    assert.deepEqual(response.body, { error: 'Unable to complete the request.' });
  }
});
test('route names use the requested casing', async () => {
  const { app } = fixture();
  await authorized(app, 'get', '/jobids').expect(404);
  await authorized(app, 'get', `/job/${JOB}`).expect(404);
});
test('configuration rejects missing values, short tokens, invalid ports and placeholders', () => {
  assert.throws(() => readConfig({}), /SUPABASE_URL/);
  const env = { SUPABASE_URL: 'https://test.supabase.co', SUPABASE_SECRET_KEY: 'secret', API_ACCESS_TOKEN: TOKEN };
  assert.equal(readConfig(env).port, 3000);
  assert.throws(() => readConfig({ ...env, PORT: 'no' }), /PORT/);
  assert.throws(() => readConfig({ ...env, API_ACCESS_TOKEN: 'short' }), /32/);
  assert.throws(() => readConfig({ ...env, SUPABASE_SECRET_KEY: 'sb_secret_REPLACE_ME' }), /SUPABASE_SECRET_KEY/);
});
