const Bull = require('bull');
const axios = require('axios');
const Redis = require('ioredis');
const {
  redisUrl,
  applicationServiceUrl,
  notificationServiceUrl,
  bookingBaseUrl,
  apiAccessToken,
} = require('../config/vars');
const logger = require('../config/logger');

let redisClient = null;
let applicationTriggerQueue = null;
let isRedisAvailable = false;

// Attempt Redis connection without crashing if Redis is not running
try {
  redisClient = new Redis(redisUrl, {
    maxRetriesPerRequest: 1,
    retryStrategy: () => null,
    enableOfflineQueue: false,
    connectTimeout: 2000,
  });

  redisClient.on('connect', () => {
    isRedisAvailable = true;
    logger.info('[REDIS] Connected successfully to Redis');
  });

  redisClient.on('error', (err) => {
    isRedisAvailable = false;
    // Suppress repeated connection logs
  });

  applicationTriggerQueue = new Bull('application-trigger', redisUrl, {
    redis: { maxRetriesPerRequest: 1 },
  });

  applicationTriggerQueue.on('error', () => {
    isRedisAvailable = false;
  });

  applicationTriggerQueue.on('failed', (job, err) => {
    logger.error('Job failed', { jobId: job.id, applicationId: job.data?.applicationId, error: err.message });
  });

  applicationTriggerQueue.on('completed', (job) => {
    logger.info('Job completed', { jobId: job.id, applicationId: job.data?.applicationId });
  });
} catch (_) {
  isRedisAvailable = false;
}

// Core processing logic shared by Bull queue and fallback executor
async function processTriggerJob(jobData) {
  const { applicationId, communicationType, name, email, phone, jobId } = jobData;
  const type = communicationType || 'email';
  let bookingLink = `${bookingBaseUrl}/book/${applicationId}`;

  // Attempt to generate official tokenized booking link with availability window
  try {
    const from = new Date(Date.now() + 3600e3 * 2).toISOString();
    const to = new Date(Date.now() + 86400e3 * 4).toISOString();
    const linkRes = await axios.post(
      `${bookingBaseUrl}/booking-links`,
      {
        applicationRef: applicationId,
        jobId,
        candidate: { name, email, phone },
        availability: {
          from,
          to,
          slotMinutes: 30,
          dailyStart: '09:00',
          dailyEnd: '18:00',
          skipWeekends: true,
        },
      },
      { timeout: 3000 }
    );
    if (linkRes.data?.url) {
      bookingLink = linkRes.data.url;
    }
  } catch (_) {}

  logger.info(`[WORKER] Processing ${type} trigger job for application ${applicationId}`, {
    candidate: name,
    type,
    bookingLink,
  });

  try {
    if (type === 'sms') {
      try {
        await axios.post(
          `${notificationServiceUrl}/notify/sms`,
          {
            phone,
            message: `Hi ${name}, we have received your application. Click here to schedule your interview: ${bookingLink}`,
            bookingUrl: bookingLink,
            candidateName: name,
            applicationId,
            jobId,
          },
          { timeout: 5000 }
        );
      } catch (err) {
        logger.warn(`[NOTIFY-SMS] Notice: notification-service endpoint unreachable or mock mode: ${err.message}`);
      }

      try {
        await axios.patch(
          `${applicationServiceUrl}/applications/${applicationId}`,
          {
            status: 'sms_sent',
            lastCommunicationAt: new Date().toISOString(),
            jobId,
          },
          {
            headers: { Authorization: `Bearer ${apiAccessToken}` },
            timeout: 5000,
          }
        );
      } catch (err) {
        logger.warn(`[APP-SERVICE] PATCH application status failed: ${err.message}`);
      }

      if (isRedisAvailable && redisClient) {
        await redisClient.setex(`pending:${applicationId}`, 48 * 60 * 60, 'sms_sent').catch(() => {});
      }

      return { applicationId, type: 'sms', status: 'sms_sent', bookingLink };
    } else if (type === 'email') {
      try {
        await axios.post(
          `${notificationServiceUrl}/notify/email`,
          {
            email,
            subject: 'Your Application - Schedule Your Interview',
            message: `Hi ${name}, thank you for applying. Please schedule your interview: ${bookingLink}`,
            bookingUrl: bookingLink,
            candidateName: name,
            applicationId,
            jobId,
          },
          { timeout: 5000 }
        );
      } catch (err) {
        logger.warn(`[NOTIFY-EMAIL] Notice: notification-service endpoint unreachable or mock mode: ${err.message}`);
      }

      try {
        await axios.patch(
          `${applicationServiceUrl}/applications/${applicationId}`,
          {
            status: 'email_sent',
            lastCommunicationAt: new Date().toISOString(),
            jobId,
          },
          {
            headers: { Authorization: `Bearer ${apiAccessToken}` },
            timeout: 5000,
          }
        );
      } catch (err) {
        logger.warn(`[APP-SERVICE] PATCH application status failed: ${err.message}`);
      }

      return { applicationId, type: 'email', status: 'email_sent', bookingLink };
    }
  } catch (err) {
    logger.error('Application trigger job failed', { applicationId, type, error: err.message });
    throw err;
  }
}

if (applicationTriggerQueue) {
  applicationTriggerQueue.process(async (job) => {
    return processTriggerJob(job.data);
  });
}

// Queue dispatcher that automatically uses Bull when Redis is available, or in-memory fallback
async function addTriggerJob(data, options = {}) {
  if (isRedisAvailable && applicationTriggerQueue) {
    try {
      return await applicationTriggerQueue.add(data, options);
    } catch (_) {
      // Fall through to direct execution if Redis add fails
    }
  }
  const delay = options.delay || 0;
  if (delay > 0) {
    setTimeout(() => {
      processTriggerJob(data).catch(() => {});
    }, Math.min(delay, 2000));
  } else {
    setImmediate(() => {
      processTriggerJob(data).catch(() => {});
    });
  }
  return { id: `inmem-${Date.now()}-${data.applicationId}`, data };
}

module.exports = {
  applicationTriggerQueue,
  processTriggerJob,
  addTriggerJob,
};
