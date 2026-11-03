const $ = id => document.getElementById(id);
const state = { token: '', view: 'jobs', job: null, jobsOffset: 0, jobsTotal: 0,
  offset: 0, limit: 10, total: 0, loading: false, inviting: false, request: 0, controller: null };
const JOBS_LIMIT = 10;

function text(id, value) { $(id).textContent = value; }
function showError(message) { text('errorBanner', message); $('errorBanner').hidden = false; }
function clearMessages() { $('errorBanner').hidden = true; $('successBanner').hidden = true; }

function controls() {
  const busy = state.loading || state.inviting;
  for (const id of ['refreshJobs', 'refreshApplications', 'pageSize', 'jobsNav', 'backToJobs', 'disconnectButton']) $(id).disabled = busy;
  $('inviteButton').disabled = busy || state.total === 0;
  $('prevJobs').disabled = busy || state.jobsOffset === 0;
  $('nextJobs').disabled = busy || state.jobsOffset + JOBS_LIMIT >= state.jobsTotal;
  $('prevPage').disabled = busy || state.offset === 0;
  $('nextPage').disabled = busy || state.offset + state.limit >= state.total;
  $('confirmInvite').disabled = state.inviting;
  $('cancelInvite').disabled = state.inviting;
  $('loadingStatus').hidden = !busy;
  text('loadingStatus', state.inviting ? 'Triggering screening pipeline…' : 'Loading workspace data…');
  for (const row of $('jobList').querySelectorAll('button')) row.disabled = busy;
  $('workspace').setAttribute('aria-busy', String(busy));
}

function disconnect(message = '') {
  if (state.inviting) return;
  state.controller?.abort();
  state.request++;
  Object.assign(state, { token: '', job: null, loading: false, total: 0, jobsTotal: 0, offset: 0, jobsOffset: 0 });
  $('inviteDialog').close();
  $('workspace').hidden = true;
  $('connectView').hidden = false;
  $('apiToken').value = '';
  $('jobList').replaceChildren();
  $('applicationsBody').replaceChildren();
  text('jobTitle', '');
  text('jobIdLabel', '');
  clearMessages();
  text('connectError', message);
  $('connectError').hidden = !message;
  $('connectButton').disabled = false;
  text('connectButton', 'Connect to workspace →');
  $('apiToken').focus();
}

async function api(path, { method = 'GET', signal } = {}) {
  let response;
  try {
    response = await fetch(path, { method, signal, cache: 'no-store',
      headers: { Authorization: `Bearer ${state.token}` } });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error(method === 'POST'
      ? 'The connection was interrupted. Refresh the applications to check whether the update completed before trying again.'
      : 'Cannot reach the server. Check that the backend is running, then try again.');
  }
  if (response.status === 401) {
    state.inviting = false;
    disconnect('Access denied. Enter the API_ACCESS_TOKEN configured on your server.');
    throw new Error('Unauthorized');
  }
  let payload;
  try { payload = await response.json(); }
  catch { throw new Error('The server returned an unexpected response. Check the backend and try again.'); }
  if (!response.ok) throw new Error(payload.error || 'The request failed. Please try again.');
  return payload;
}

function pageInfo(offset, limit, total) {
  return { number: total ? Math.floor(offset / limit) + 1 : 1,
    pages: Math.max(1, Math.ceil(total / limit)),
    range: total ? `${offset + 1}–${Math.min(offset + limit, total)} of ${total}` : '0 of 0' };
}

function element(tag, className, value) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value !== undefined) node.textContent = value;
  return node;
}

function renderJobs(payload) {
  state.jobsTotal = payload.pagination.total;
  text('totalJobs', state.jobsTotal);
  text('jobCount', `${state.jobsTotal} ${state.jobsTotal === 1 ? 'job' : 'jobs'}`);
  const fragment = document.createDocumentFragment();
  for (const job of payload.data) {
    const row = element('button', 'job-row');
    const symbol = element('span', 'job-symbol', '▦'); symbol.setAttribute('aria-hidden', 'true');
    const main = element('span', 'job-main');
    main.append(element('strong', '', job.description), element('small', '', job.jobId));
    const chevron = element('span', 'chevron', '›'); chevron.setAttribute('aria-hidden', 'true');
    row.append(symbol, main, element('span', 'view-applicants', 'View applicants'), chevron);
    row.addEventListener('click', () => { if (!state.loading && !state.inviting) openJob(job); });
    fragment.append(row);
  }
  if (!payload.data.length) fragment.append(element('div', 'empty-state', 'No jobs yet.'));
  $('jobList').replaceChildren(fragment);
  const page = pageInfo(state.jobsOffset, JOBS_LIMIT, state.jobsTotal);
  text('jobsPaginationInfo', `Showing ${page.range} jobs`);
  text('jobsPageNumber', `${page.number} / ${page.pages}`);
}

function statusCell(value) {
  const cell = element('td');
  let cls = 'status';
  if (value === 'SMS_SENT' || value === 'EMAIL_SENT' || value === 'Invited Candidates') cls += ' invited';
  if (value === 'BOOKED' || value === 'Interview Scheduled') cls += ' booked';
  if (value === 'SCREENING_PASSED') cls += ' passed';
  if (value === 'SCREENING_FAILED' || value === 'UNRESPONSIVE') cls += ' failed';
  cell.append(element('span', cls, value ?? '—'));
  return cell;
}

function renderApplications(payload) {
  state.total = payload.pagination.total;
  state.job = payload.job;
  text('jobTitle', payload.job.description);
  text('jobIdLabel', `Job ID: ${payload.job.jobId}`);
  text('applicationTotal', state.total);
  const page = pageInfo(state.offset, state.limit, state.total);
  text('currentPageLabel', `${page.number} of ${page.pages}`);
  text('pageNumber', `${page.number} / ${page.pages}`);
  text('paginationInfo', `Showing ${page.range} applications`);
  const fragment = document.createDocumentFragment();

  for (const application of payload.applications) {
    const row = element('tr');
    const candidate = application.candidate;
    const name = candidate?.name || 'Candidate unavailable';
    const details = element('div', 'candidate-cell');
    const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map(word => word[0]).join('').toUpperCase();
    const avatar = element('span', 'candidate-avatar', initials); avatar.setAttribute('aria-hidden', 'true');
    const copy = element('span', 'candidate-copy');
    copy.append(element('strong', '', name), element('small', '', candidate?.email || '—'),
      element('small', 'candidate-id', application.candidateId));
    details.append(avatar, copy);
    const candidateCell = element('td'); candidateCell.append(details);

    const bookingCell = element('td');
    const bookingLink = document.createElement('a');
    bookingLink.href = `http://localhost:3001/book/${application.applicationId}`;
    bookingLink.target = '_blank';
    bookingLink.className = 'btn btn-quiet';
    bookingLink.style.cssText = 'padding:4px 8px;font-size:0.75rem;text-decoration:none;display:inline-block;white-space:nowrap;';
    bookingLink.textContent = 'Book Slot ↗';
    bookingCell.append(bookingLink);

    row.append(
      candidateCell,
      element('td', '', candidate?.phone || '—'),
      element('td', 'id-cell', application.applicationId),
      statusCell(application.candidateStatus),
      statusCell(application.applicationStatus)
    );
    for (const consent of [candidate?.smsConsent, candidate?.emailConsent]) {
      row.append(element('td', `consent${consent === true ? ' yes' : ''}`, consent === true ? 'Yes' : consent === false ? 'No' : '—'));
    }
    row.append(bookingCell);
    fragment.append(row);
  }
  $('applicationsBody').replaceChildren(fragment);
  $('applicationsEmpty').hidden = payload.applications.length > 0;
  text('applicationsEmpty', 'No applications for this job yet.');
}

async function load(view = state.view) {
  state.controller?.abort();
  const controller = new AbortController(); state.controller = controller;
  const request = ++state.request;
  state.view = view;
  state.loading = true;
  clearMessages();
  controls();
  try {
    const path = view === 'jobs'
      ? `/jobIDs?limit=${JOBS_LIMIT}&offset=${state.jobsOffset}`
      : `/Job/${encodeURIComponent(state.job.jobId)}?limit=${state.limit}&offset=${state.offset}`;
    const payload = await api(path, { signal: controller.signal });
    if (request !== state.request) return;
    if (view === 'jobs') renderJobs(payload);
    else renderApplications(payload);
  } catch (error) {
    if (request !== state.request || error.name === 'AbortError') return;
    showError(error.message);
  } finally {
    if (request === state.request) {
      state.loading = false;
      controls();
    }
  }
}

async function openJob(job) {
  state.job = job;
  state.offset = 0;
  $('jobsView').hidden = true;
  $('applicationsView').hidden = false;
  text('crumb', job.description);
  await load('applications');
}

function showJobs() {
  state.job = null;
  state.offset = 0;
  $('applicationsView').hidden = true;
  $('jobsView').hidden = false;
  text('crumb', 'Job applications');
  load('jobs');
}

$('connectForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const token = $('apiToken').value.trim();
  if (token.length < 32) {
    text('connectError', 'API access token must be at least 32 characters.');
    $('connectError').hidden = false;
    return;
  }
  state.token = token;
  $('connectError').hidden = true;
  $('connectButton').disabled = true;
  text('connectButton', 'Connecting…');
  try {
    const payload = await api(`/jobIDs?limit=${JOBS_LIMIT}&offset=0`);
    $('connectView').hidden = true;
    $('workspace').hidden = false;
    showJobs();
    renderJobs(payload);
  } catch (error) {
    disconnect(error.message);
  }
});

$('fillDemoTokenBtn')?.addEventListener('click', () => {
  $('apiToken').value = 'hire2hack-recruiter-demo-api-token-2026-v1';
});

$('disconnectButton').addEventListener('click', () => disconnect());
$('refreshJobs').addEventListener('click', () => load('jobs'));
$('refreshApplications').addEventListener('click', () => load('applications'));
$('backToJobs').addEventListener('click', showJobs);
$('jobsNav').addEventListener('click', showJobs);
$('homeLink').addEventListener('click', (e) => { e.preventDefault(); showJobs(); });

$('prevJobs').addEventListener('click', () => {
  if (state.jobsOffset > 0) {
    state.jobsOffset = Math.max(0, state.jobsOffset - JOBS_LIMIT);
    load('jobs');
  }
});

$('nextJobs').addEventListener('click', () => {
  if (state.jobsOffset + JOBS_LIMIT < state.jobsTotal) {
    state.jobsOffset += JOBS_LIMIT;
    load('jobs');
  }
});

$('prevPage').addEventListener('click', () => {
  if (state.offset > 0) {
    state.offset = Math.max(0, state.offset - state.limit);
    load('applications');
  }
});

$('nextPage').addEventListener('click', () => {
  if (state.offset + state.limit < state.total) {
    state.offset += state.limit;
    load('applications');
  }
});

$('pageSize').addEventListener('change', (e) => {
  state.limit = Number(e.target.value);
  state.offset = 0;
  load('applications');
});

$('inviteButton').addEventListener('click', () => {
  text('confirmCount', state.total);
  $('inviteDialog').showModal();
});

$('cancelInvite').addEventListener('click', () => $('inviteDialog').close());

$('confirmInvite').addEventListener('click', async () => {
  if (state.inviting) return;
  state.inviting = true;
  clearMessages();
  controls();
  text('confirmInvite', 'Dispatching…');
  try {
    const result = await api(`/invite/${encodeURIComponent(state.job.jobId)}`, { method: 'POST' });
    $('inviteDialog').close();
    state.inviting = false;
    await load('applications');
    if (!state.token) return;
    text('successBanner', `${result.updatedCount} candidate invitation(s) triggered! Orchestrator and notification pipeline active.`);
    $('successBanner').hidden = false;
  } catch (error) {
    $('inviteDialog').close();
    if (state.token) showError(error.message);
  } finally {
    state.inviting = false;
    text('confirmInvite', 'Send Invitations');
    controls();
  }
});
