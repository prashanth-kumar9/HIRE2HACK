# HIRE2HACK — Automated Screening Platform (ASO)

An end-to-end distributed recruitment screening and interview scheduling platform built for the Phenom HIRE2HACK Hackathon.

All microservices across the repository are fully integrated, tested, and interconnected into a functionally working project.

---

## 🏗️ System Architecture

Based on the system design specification (`system design 1.png` & `system design.jpeg`):

```
                   +----------------------------------+
                   |    Recruiter Web Workspace UI    |
                   |   (http://localhost:3002)        |
                   +-----------------+----------------+
                                     |
                                     v
                        [ application_service ]
               (Recruiter API, Job / Candidate Database)
                                     |
               1. Trigger Invites    |  2. Fetches Candidates
                                     v
                        [ monitoring-service ]
               (Bull Queue Orchestrator & Rate Limiter)
                    /                          \
         Requests Slots                      Dispatches SMS / Email
                  /                              \
                 v                                v
    [ Candidate Booking Portal ]        [ notification_service ]
      (pranav_kulkarni / Phenom)        (Hafeez / Spring Boot + Templates)
   - Self-serve slot booking (3001)     - SMS Dispatch (Twilio mock/API)
   - Reschedule link generator          - Email Dispatch
   - Webhook outbox emitter             - Preference checks (Consent)
                 |
        Booking Webhooks
                 |
                 +-------------------+
                                     |
                                     v
                      [ interview-booking-service ]
                      - Voice AI Screening Provider
                      - Call results & scoring (>= 70 pass)
                      - Missed call rebooking workflows
                      - Buffer & delay timeout monitors
```

---

## 📦 Services Overview & Port Map

| Service | Technology | Port | Description |
|---|---|---|---|
| **Recruiter Workspace & API** (`application_service`) | Node.js 22+, Express, Supabase / In-Memory DB | `3002` | Recruiter dashboard to review jobs, browse candidate applications, trigger invitations, and monitor screening scores. |
| **Candidate Booking Portal** (`pranav_kulkarni/Phenom dot dot`) | Node.js 22+, Express, SQLite (`node:sqlite`) | `3001` | Candidate-facing responsive web portal where candidates select available 30-min interview slots and reschedule if needed. |
| **Communication Orchestrator** (`monitoring-service`) | Node.js, Express, Bull / In-Process Queue | `3003` | Batches candidates by consent type (SMS vs Email), rate-limits invitations, requests personalized tokens, and schedules 24h/48h follow-ups. |
| **Notification Service** (`notification_service_hafeez`) | Java 21, Spring Boot 3.3, Kafka | `8081` | Template engine (SMS & Email), consent verification, idempotent delivery, HTTP API & Kafka event consumer. |
| **Interview Booking & Voice Engine** (`interview-booking-service`) | Java 21, Spring Boot, MongoDB, Kafka | `8080` | High-concurrency slot validation (DSA algorithm), voice provider webhook callback (`/api/v1/screening/call-results`), scoring, and missed-call recovery. |
| **Application Tracker & Scheduler** (`hackathon-main-prince`) | Java 21, Spring Boot, MySQL | `8082` | Alternative application tracking service with scheduled notification reminders. |

---

## 🚀 Quick Start (Single Command)

### 1. Launch All Integrated Services
From the repository root:
```bash
npm start
```
This concurrently starts:
- Recruiter Dashboard on `http://localhost:3002`
- Candidate Booking Portal on `http://localhost:3001`
- Monitoring Orchestrator on `http://localhost:3003`
- Notification Service on `http://localhost:8081`

### 2. Run the End-to-End Integration Test Suite
To automatically boot all services, execute the full recruitment & voice screening lifecycle, verify outputs, and shut down:
```bash
npm test
```

---

## 🔄 End-to-End Workflow Demonstration

1. **Recruiter Review & Invite**:
   - Open **`http://localhost:3002`**
   - Click **Demo Token** (or use `hire2hack-recruiter-demo-api-token-2026-v1`) and connect.
   - Select the job **"Node.js Backend Developer"**.
   - Click **"Invite all candidates"**.
2. **Orchestration & Queueing**:
   - `application_service` calls `monitoring-service` at `POST /orchestrate`.
   - `monitoring-service` batches candidates:
     - Alex Kumar (SMS consent: `true`) -> SMS Queue
     - Sam Rao (SMS consent: `false`) -> Email Queue
   - Personalized self-serve booking links are requested from the Candidate Portal.
3. **Delivery via Notification Service**:
   - SMS and Email messages are rendered using notification templates and delivered with the candidate's custom booking link.
4. **Candidate Booking Experience**:
   - Candidate clicks the link: **`http://localhost:3001/book/<applicationId>`**
   - Selects an interview slot (e.g. 10:00 AM) and clicks **"Book interview"**.
   - Confirmation is displayed with a secret rescheduling link.
5. **Real-time Recruiter Synchronization**:
   - A transactional webhook `POST /webhooks/bookings` notifies `application_service`.
   - Recruiter table immediately updates candidate status to **`BOOKED`**.
6. **AI Voice Screening & Scoring**:
   - At interview time, the voice screening provider conducts the screening and submits results to `POST /api/v1/screening/call-results`.
   - Candidates scoring $\ge 70$ pass; status updates to **`SCREENING_PASSED`** (e.g. 88 / 100) on the recruiter dashboard!

---

## 🧪 Unit Test Coverage Across Services

All test suites pass 100%:

```bash
# Application Service tests (12 tests)
npm run test:app

# Candidate Booking Portal tests (5 tests)
npm run test:portal

# Notification Service tests (42 tests)
npm run test:notification

# Interview Booking Service tests (30 tests)
npm run test:booking
```
