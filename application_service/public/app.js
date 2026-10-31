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
  text('loadingStatus', state.inviting ? 'Updating application statuses…' : 'Loading workspace data…');
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
    // Reset before handling the error so no candidate details remain on screen.
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
  if (!payload.data.length) fragment.append(element('div', 'empty-state', 'No jobs yet. Add a job in Supabase to get started.'));
  $('jobList').replaceChildren(fragment);
  const page = pageInfo(state.jobsOffset, JOBS_LIMIT, state.jobsTotal);
  text('jobsPaginationInfo', `Showing ${page.range} jobs`);
  text('jobsPageNumber', `${page.number} / ${page.pages}`);
}

function statusCell(value) {
  const cell = element('td');
  const invited = value === 'SMS_SENT' || value === 'Invited Candidates';
  cell.append(element('span', `status${invited ? ' invited' : ''}`, value ?? '—'));
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
    row.append(candidateCell, element('td', '', candidate?.phone || '—'),
      element('td', 'id-cell', application.applicationId), statusCell(application.candidateStatus), statusCell(application.applicationStatus));
    for (const consent of [candidate?.smsConsent, candidate?.emailConsent]) {
      row.append(element('td', `consent${consent === true ? ' yes' : ''}`, consent === true ? 'Yes' : consent === false ? 'No' : '—'));
    }
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
    // Rows may have been removed since the user last viewed the previous page.
    const offset = view === 'jobs' ? state.jobsOffset : state.offset;
    if (offset && offset >= payload.pagination.total) {
      if (view === 'jobs') state.jobsOffset = 0; else state.offset = 0;
      return await load(view);
    }
    if (view === 'jobs') renderJobs(payload); else renderApplications(payload);
    $('connectView').hidden = true;
    $('workspace').hidden = false;
    $('jobsView').hidden = view !== 'jobs';
    $('applicationsView').hidden = view !== 'applications';
    text('crumb', view === 'jobs' ? 'Job applications' : 'Applicants');
    $('apiToken').value = '';
  } catch (error) {
    if (request !== state.request || error.name === 'AbortError') return;
    if ($('workspace').hidden) {
      text('connectError', error.message); $('connectError').hidden = false;
    } else showError(`${error.message} Use Refresh to retry.`);
  } finally {
    if (request === state.request) {
      state.loading = false;
      controls();
      $('connectButton').disabled = false;
      text('connectButton', 'Connect to workspace →');
    }
  }
}

function openJob(job) {
  state.job = job;
  state.offset = 0;
  state.total = 0;
  $('applicationsBody').replaceChildren();
  text('jobTitle', job.description);
  text('jobIdLabel', `Job ID: ${job.jobId}`);
  text('applicationTotal', '—');
  text('currentPageLabel', '—');
  $('jobsView').hidden = true;
  $('applicationsView').hidden = false;
  load('applications');
}

$('connectForm').addEventListener('submit', event => {
  event.preventDefault();
  state.token = $('apiToken').value.trim();
  if (!state.token) return;
  $('connectError').hidden = true;
  $('connectButton').disabled = true;
  text('connectButton', 'Connecting…');
  load('jobs');
});
function backToJobs(event) {
  event.preventDefault();
  if (!state.loading && !state.inviting) load('jobs');
}
for (const id of ['homeLink', 'jobsNav', 'backToJobs']) $(id).addEventListener('click', backToJobs);
$('disconnectButton').addEventListener('click', () => disconnect());
$('refreshJobs').addEventListener('click', () => load('jobs'));
$('refreshApplications').addEventListener('click', () => load('applications'));
$('prevJobs').addEventListener('click', () => { state.jobsOffset = Math.max(0, state.jobsOffset - JOBS_LIMIT); load('jobs'); });
$('nextJobs').addEventListener('click', () => { state.jobsOffset += JOBS_LIMIT; load('jobs'); });
$('prevPage').addEventListener('click', () => { state.offset = Math.max(0, state.offset - state.limit); load('applications'); });
$('nextPage').addEventListener('click', () => { state.offset += state.limit; load('applications'); });
$('pageSize').addEventListener('change', () => { state.limit = Number($('pageSize').value); state.offset = 0; load('applications'); });
$('inviteButton').addEventListener('click', () => { text('confirmCount', state.total); $('inviteDialog').showModal(); });
$('cancelInvite').addEventListener('click', () => $('inviteDialog').close());
$('inviteDialog').addEventListener('cancel', event => { if (state.inviting) event.preventDefault(); });
$('confirmInvite').addEventListener('click', async () => {
  if (state.inviting) return;
  state.inviting = true;
  clearMessages();
  controls();
  text('confirmInvite', 'Updating…');
  try {
    const result = await api(`/invite/${encodeURIComponent(state.job.jobId)}`, { method: 'POST' });
    $('inviteDialog').close();
    state.inviting = false;
    await load('applications');
    if (!state.token) return;
    text('successBanner', `${result.updatedCount} application${result.updatedCount === 1 ? '' : 's'} updated to SMS_SENT / Invited Candidates. No SMS was sent.`);
    $('successBanner').hidden = false;
  } catch (error) {
    $('inviteDialog').close();
    if (state.token) showError(error.message);
  } finally {
    state.inviting = false;
    text('confirmInvite', 'Update applications');
    controls();
  }
});
