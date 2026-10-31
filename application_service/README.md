# HIRE2HACK — Job application API

Node.js (22+) and Express API backed by Supabase PostgreSQL. Includes the four requested tables, UUID identifiers, foreign keys, pagination, validation, and JSON errors.

## 1. Create the Supabase tables

1. Sign in to [Supabase](https://supabase.com/dashboard) and create a project. Wait for provisioning to finish.
2. Open **SQL Editor → New query**, paste [`supabase/schema.sql`](supabase/schema.sql), and click **Run**. Run this script once in a fresh project; it creates all four tables in one transaction.
3. Optionally run [`supabase/seed.sql`](supabase/seed.sql). This adds two jobs, two candidates, three applications, and one booking. It can be rerun without overwriting statuses.
4. Open **Table Editor** to view or add records. Create jobs and candidates before applications, then bookings. UUIDs are generated automatically when omitted.

The SQL uses quoted names to match your model (`"Job"`, `"jobId"`, etc.). Use double quotes when writing SQL against these names. A candidate can apply once per job and have multiple bookings. Booking dates use `timestamptz`; supply ISO 8601 timestamps with an explicit timezone. The booking foreign key prevents assigning another candidate's application to a booking. Deleting referenced records is blocked to preserve application history.

Row Level Security is enabled on all tables. Direct access for `anon` and `authenticated` is revoked. This backend uses a server-only privileged key. See [Supabase table documentation](https://supabase.com/docs/guides/database/tables).

## 2. Get the Supabase URL and API key

1. Open your project's **Connect** dialog to find the project URL, such as `https://YOUR_PROJECT_REF.supabase.co`.
2. Open **Settings → API Keys** and create or copy a **secret key** (`sb_secret_...`) for this server. A legacy `service_role` key also works as the value of `SUPABASE_SECRET_KEY`.
3. Put both values in `.env`. Use the project API URL, not a PostgreSQL connection string.

Secret keys bypass Row Level Security. Keep them on the backend and out of source control and browser code. The separate `API_ACCESS_TOKEN` below controls access to this Node.js API; it is not the Supabase key. See [Supabase's API key guide](https://supabase.com/docs/guides/getting-started/api-keys).

## 3. Configure and run

```sh
npm install
cp .env.example .env
openssl rand -hex 32
```

Edit `.env`: set `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, and use the generated random string for `API_ACCESS_TOKEN`. `PORT` defaults to `3000`.

```sh
npm run dev
# Or without automatic restarts:
npm start
```

Every endpoint requires `Authorization: Bearer <API_ACCESS_TOKEN>`. This starter uses a shared token for trusted backend/admin callers and grants access to all candidates. Use per-user authentication and authorization before supporting individual candidate logins. Use HTTPS when hosting the service.

## Frontend workspace

Open **http://localhost:3000** after starting the server. The frontend is served by Express, so no separate frontend installation or CORS configuration is needed. Enter your `API_ACCESS_TOKEN` to connect. The token is kept only in page memory and cleared on disconnect/reload; the Supabase secret key stays on the server.

The UI adapts the supplied ScreenFlow screens to the existing APIs: paginated jobs, paginated applications with candidate name/email/phone/ID and both consent fields, candidate/application statuses, refresh, and a confirmation dialog for inviting all applications for a job. There are no eligibility rules, applied dates, notification history, delivery metrics, or booking actions because the APIs do not provide those features. The invite action updates every application for the selected job, including candidates without SMS consent; it does not send SMS.

On the corporate network, use the working proxy configuration with Node.js 24.5+:

```sh
HTTPS_PROXY=http://www-proxy-hqdc.us.oracle.com:80 \
NODE_OPTIONS="--use-env-proxy --use-system-ca" \
npm start
```

Keep TLS verification enabled. Outside that network, use `npm start` if direct access works.

## 4. APIs

Routes are case sensitive. Replace `{jobId}` with an actual UUID. For the examples, set a shell variable to the same API token from `.env`:

```sh
export API_ACCESS_TOKEN='your-generated-api-token'
```

### GET /jobIDs

Returns job IDs and descriptions. Optional `limit` (1–100, default 50) and `offset` (default 0) support pagination. Use `pagination.total` to fetch subsequent pages.

```sh
curl 'http://localhost:3000/jobIDs?limit=50&offset=0' \
  -H "Authorization: Bearer $API_ACCESS_TOKEN"
```

```json
{
  "data": [
    { "jobId": "11111111-1111-4111-8111-111111111111", "description": "Node.js Backend Developer" }
  ],
  "pagination": { "limit": 50, "offset": 0, "total": 1 }
}
```

This example shows one job; the optional seed contains two.

### GET /Job/{jobId}

Returns job details and applications, each with candidate contact and consent details. Supports the same `limit` and `offset`. A job with no applicants returns `applications: []`; an unknown job returns 404.

```sh
curl 'http://localhost:3000/Job/11111111-1111-4111-8111-111111111111' \
  -H "Authorization: Bearer $API_ACCESS_TOKEN"
```

```json
{
  "job": { "jobId": "11111111-1111-4111-8111-111111111111", "description": "Node.js Backend Developer" },
  "applications": [
    {
      "applicationId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      "candidateId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "jobId": "11111111-1111-4111-8111-111111111111",
      "candidateStatus": "NEW",
      "applicationStatus": "Applied",
      "candidate": {
        "candidateId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "name": "Alex Kumar",
        "email": "alex@example.com",
        "phone": "+12025550101",
        "smsConsent": true,
        "emailConsent": true
      }
    }
  ],
  "pagination": { "limit": 50, "offset": 0, "total": 2 }
}
```

The application list above is abbreviated; the seeded job has two applicants.

### POST /invite/{jobId}

Sets `candidateStatus` to `SMS_SENT` and `applicationStatus` to `Invited Candidates` on **every application for this job**, including candidates with `smsConsent: false`. This endpoint implements the requested status update: no SMS provider is called and consent flags are unchanged. An actual SMS integration should check consent and set `SMS_SENT` only after a successful send.

Send no body or `{}`. Nonempty JSON bodies are rejected to prevent accidentally treating a candidate selector as a bulk invitation. Both statuses change together in one database statement. Applications for other jobs are untouched. Repeating the request leaves the same statuses; `updatedCount` counts matching rows, including those already invited. A job with no applicants returns zero; an unknown job returns 404.

```sh
curl -X POST 'http://localhost:3000/invite/11111111-1111-4111-8111-111111111111' \
  -H "Authorization: Bearer $API_ACCESS_TOKEN"
```

```json
{
  "jobId": "11111111-1111-4111-8111-111111111111",
  "updatedCount": 2,
  "candidateStatus": "SMS_SENT",
  "applicationStatus": "Invited Candidates"
}
```

Run `GET /Job/{jobId}` again to verify the stored statuses.

### Errors

Errors use `{ "error": "message" }`: 400 for invalid UUIDs, pagination, or JSON bodies; 401 for a missing/incorrect API token; 404 for unknown jobs/routes; 413 for oversized bodies; 500 for database failures. Database details and credentials are not returned to callers.

## Tests

```sh
npm test
```

If installation fails against a corporate registry, use an accessible approved npm registry. An expired TLS certificate must be fixed in the registry/proxy configuration; keep certificate verification enabled.

Tests exercise HTTP routes and the real Supabase client with a simulated PostgREST transport: job-scoped updates, authentication, candidate joins, pagination, empty results, invalid inputs, and database errors. They do not contact a live Supabase database. To validate your deployment, run the schema and seed, configure `.env`, and run the three curl examples.

Booking storage is included in the schema. Booking endpoints and candidate/job creation endpoints are outside the requested three APIs; add initial records through the Table Editor or seed SQL.
