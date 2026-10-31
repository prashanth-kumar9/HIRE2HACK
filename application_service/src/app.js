import express from 'express';
import { timingSafeEqual } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function pagination(query) {
  const { limit = '50', offset = '0' } = query;
  if (typeof limit !== 'string' || typeof offset !== 'string'
      || !/^\d+$/.test(limit) || !/^\d+$/.test(offset)
      || !Number.isSafeInteger(Number(offset)) || Number(offset) > 2147483547
      || Number(limit) < 1 || Number(limit) > 100) {
    return null;
  }
  return { limit: Number(limit), offset: Number(offset) };
}

export function createApp({ supabase, apiToken, logger = console }) {
  if (!apiToken || apiToken.length < 32) throw new Error('A valid API token is required.');
  const app = express();
  app.disable('x-powered-by');
  app.set('case sensitive routing', true);
  // Public UI assets contain no credentials. Data endpoints below still require
  // the API token supplied by the recruiter in the browser.
  app.use((req, res, next) => {
    res.set('X-Content-Type-Options', 'nosniff');
    res.set('Referrer-Policy', 'no-referrer');
    res.set('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    next();
  });
  app.use(express.static(fileURLToPath(new URL('../public/', import.meta.url)), {
    index: 'index.html', dotfiles: 'deny', maxAge: 0,
  }));
  app.use((req, res, next) => {
    res.set('Cache-Control', 'no-store');
    const actual = Buffer.from(req.get('authorization') ?? '');
    const expected = Buffer.from(`Bearer ${apiToken}`);
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
      return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
  });
  app.use(express.json({ limit: '16kb' }));

  app.param('jobId', (req, res, next, value) => {
    if (!UUID.test(value)) return res.status(400).json({ error: 'jobId must be a UUID.' });
    next();
  });

  async function findJob(jobId) {
    const { data, error } = await supabase.from('Job')
      .select('jobId,description').eq('jobId', jobId).maybeSingle();
    if (error) throw error;
    return data;
  }

  app.get('/jobIDs', async (req, res) => {
    const page = pagination(req.query);
    if (!page) return res.status(400).json({ error: 'Use limit=1..100 and a nonnegative integer offset.' });
    const { data, error, count } = await supabase.from('Job')
      .select('jobId,description', { count: 'exact' }).order('jobId')
      .range(page.offset, page.offset + page.limit - 1);
    if (error) throw error;
    res.json({ data, pagination: { ...page, total: count } });
  });

  app.get('/Job/:jobId', async (req, res) => {
    const page = pagination(req.query);
    if (!page) return res.status(400).json({ error: 'Use limit=1..100 and a nonnegative integer offset.' });
    const job = await findJob(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'Job not found.' });
    const { data, error, count } = await supabase.from('Application')
      .select('applicationId,candidateId,jobId,candidateStatus,applicationStatus,candidate:Candidate(candidateId,name,email,phone,smsConsent,emailConsent)', { count: 'exact' })
      .eq('jobId', req.params.jobId).order('applicationId')
      .range(page.offset, page.offset + page.limit - 1);
    if (error) throw error;
    res.json({ job, applications: data, pagination: { ...page, total: count } });
  });

  app.post('/invite/:jobId', async (req, res) => {
    if (req.body !== undefined && (req.body === null || Array.isArray(req.body)
        || Object.keys(req.body).length > 0)) {
      return res.status(400).json({ error: 'Send no body or {}. This endpoint invites all applications for the job.' });
    }
    const job = await findJob(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'Job not found.' });
    // Both fields are changed by one database statement, scoped to this job.
    // No row payload is requested, so the result count is not limited by row caps.
    const { error, count } = await supabase.from('Application').update({
      candidateStatus: 'SMS_SENT',
      applicationStatus: 'Invited Candidates',
    }, { count: 'exact' }).eq('jobId', req.params.jobId);
    if (error) throw error;
    res.json({ jobId: req.params.jobId, updatedCount: count,
      candidateStatus: 'SMS_SENT', applicationStatus: 'Invited Candidates' });
  });

  app.use((req, res) => res.status(404).json({ error: 'Route not found.' }));
  app.use((error, req, res, next) => {
    if (error.type === 'entity.parse.failed') return res.status(400).json({ error: 'Invalid JSON body.' });
    if (error.type === 'entity.too.large') return res.status(413).json({ error: 'Request body too large.' });
    // Transport failures may have an empty code; include their diagnostic message
    // in server logs while keeping the HTTP response generic.
    logger.error('API request failed', {
      code: error.code || error.cause?.code || 'UNKNOWN',
      message: error.message || 'No error message provided.',
      details: error.details || undefined,
      hint: error.hint || undefined,
    });
    res.status(500).json({ error: 'Unable to complete the request.' });
  });
  return app;
}
