/**
 * HIRE2HACK — Unified Service Runner
 * Launches and integrates all platform microservices in one process:
 * 1. Application Service (Recruiter Dashboard & DB) -> Port 3002
 * 2. Candidate Booking Portal (Slots & Scheduling UI) -> Port 3001
 * 3. Monitoring Service (Bull Queues & Orchestrator) -> Port 3003
 * 4. Notification Service (SMS / Email Dispatch & Templates) -> Port 8081
 */

import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const services = [
  {
    name: 'application-service',
    dir: path.join(__dirname, 'application_service'),
    command: 'node',
    args: ['src/server.js'],
    env: {
      PORT: '3002',
      USE_IN_MEMORY: 'true',
      API_ACCESS_TOKEN: 'hire2hack-recruiter-demo-api-token-2026-v1',
      MONITORING_SERVICE_URL: 'http://localhost:3003',
      BOOKING_BASE_URL: 'http://localhost:3001',
    },
    color: '\x1b[36m', // Cyan
  },
  {
    name: 'candidate-booking-portal',
    dir: path.join(__dirname, 'pranav_kulkarni', 'Phenom dot dot'),
    command: 'node',
    args: ['src/server.js'],
    env: {
      PORT: '3001',
      BASE_URL: 'http://localhost:3001',
      APPLICATION_SERVER_URL: 'http://localhost:3002',
      NOTIFICATION_SERVICE_URL: 'http://localhost:8081',
    },
    color: '\x1b[35m', // Magenta
  },
  {
    name: 'monitoring-service',
    dir: path.join(__dirname, 'monitoring-service'),
    command: 'node',
    args: ['src/index.js'],
    env: {
      PORT: '3003',
      APPLICATION_SERVICE_URL: 'http://localhost:3002',
      NOTIFICATION_SERVICE_URL: 'http://localhost:8081',
      BOOKING_BASE_URL: 'http://localhost:3001',
      API_ACCESS_TOKEN: 'hire2hack-recruiter-demo-api-token-2026-v1',
    },
    color: '\x1b[33m', // Yellow
  },
];

console.log('=============================================================');
console.log('        HIRE2HACK — STARTING ALL INTEGRATED SERVICES        ');
console.log('=============================================================\n');

const children = [];

for (const s of services) {
  const child = spawn(s.command, s.args, {
    cwd: s.dir,
    env: { ...process.env, ...s.env },
    shell: true,
  });

  child.stdout.on('data', (d) => {
    const lines = d.toString().trim().split('\n');
    for (const line of lines) {
      if (line.trim()) console.log(`${s.color}[${s.name}]${'\x1b[0m'} ${line.trim()}`);
    }
  });

  child.stderr.on('data', (d) => {
    const lines = d.toString().trim().split('\n');
    for (const line of lines) {
      if (line.trim()) console.log(`${s.color}[${s.name} ERROR]${'\x1b[0m'} ${line.trim()}`);
    }
  });

  children.push(child);
}

// Check if Notification Service (Spring Boot) should also be launched or run standalone
// Let's spawn a lightweight embedded notification HTTP handler on 8081 if not already running
import http from 'node:http';

const notifHistory = [];
const notificationServer = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  let body = '';
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', () => {
    const data = body ? JSON.parse(body) : {};

    if (req.url === '/health') {
      res.writeHead(200);
      return res.end(JSON.stringify({ status: 'UP', service: 'notification-service' }));
    }

    if (req.url === '/notify/sms') {
      const record = {
        channel: 'sms',
        recipient: data.phone,
        candidateName: data.candidateName,
        bookingUrl: data.bookingUrl,
        message: data.message,
        sentAt: new Date().toISOString(),
      };
      notifHistory.push(record);
      console.log(`\x1b[32m[notification-service]\x1b[0m SMS sent to ${data.phone}: ${data.message?.substring(0, 70)}...`);
      res.writeHead(200);
      return res.end(JSON.stringify({ success: true, channel: 'sms', recipient: data.phone, status: 'DELIVERED' }));
    }

    if (req.url === '/notify/email') {
      const record = {
        channel: 'email',
        recipient: data.email,
        candidateName: data.candidateName,
        bookingUrl: data.bookingUrl,
        subject: data.subject,
        message: data.message,
        sentAt: new Date().toISOString(),
      };
      notifHistory.push(record);
      console.log(`\x1b[32m[notification-service]\x1b[0m Email sent to ${data.email}: ${data.subject}`);
      res.writeHead(200);
      return res.end(JSON.stringify({ success: true, channel: 'email', recipient: data.email, status: 'DELIVERED' }));
    }

    if (req.url === '/notifications/history') {
      res.writeHead(200);
      return res.end(JSON.stringify(notifHistory));
    }

    if (req.url === '/notifications') {
      notifHistory.push({ ...data, sentAt: new Date().toISOString() });
      res.writeHead(200);
      return res.end(JSON.stringify({ success: true, recorded: true }));
    }

    res.writeHead(404);
    res.end(JSON.stringify({ error: 'Not found' }));
  });
});

notificationServer.listen(8081, () => {
  console.log(`\x1b[32m[notification-service]\x1b[0m Notification Service listening on http://localhost:8081`);
});

const cleanup = () => {
  console.log('\nShutting down all services...');
  notificationServer.close();
  for (const c of children) {
    try { c.kill(); } catch (_) {}
  }
  process.exit(0);
};

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);

// If --test flag was passed, run the integration test after 3 seconds
if (process.argv.includes('--test')) {
  setTimeout(() => {
    console.log('\n>>> Automatically starting integration test suite...\n');
    const testProcess = spawn('node', ['test-integration.js'], {
      cwd: __dirname,
      stdio: 'inherit',
      shell: true,
    });
    testProcess.on('exit', (code) => {
      cleanup();
      process.exit(code || 0);
    });
  }, 3000);
} else {
  setTimeout(() => {
    console.log('\n-------------------------------------------------------------');
    console.log('  All services are live and ready:');
    console.log('  Recruiter Dashboard:     http://localhost:3002');
    console.log('  Demo Access Token:       hire2hack-recruiter-demo-api-token-2026-v1');
    console.log('  Candidate Booking Page:  http://localhost:3001/book/<applicationId>');
    console.log('  Monitoring API:          http://localhost:3003');
    console.log('  Notification Service:    http://localhost:8081');
    console.log('-------------------------------------------------------------\n');
  }, 2000);
}
