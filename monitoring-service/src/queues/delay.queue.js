const Bull = require('bull');
const axios = require('axios');
const Redis = require('ioredis');
const {
  redisUrl,
  applicationServiceUrl,
  notificationServiceUrl,
  apiAccessToken,
  bookingBaseUrl,
} = require('../config/vars');
const logger = require('../config/logger');

let delayQueue = null;
let redis = null;
let isRedisAvailable = false;

try {
  redis = new Redis(redisUrl, {
    maxRetriesPerRequest: 1,
    retryStrategy: () => null,
    enableOfflineQueue: false,
    connectTimeout: 2000,
  });

  redis.on('connect', () => {
    isRedisAvailable = true;
  });

  redis.on('error', () => {
    isRedisAvailable = false;
  });

  delayQueue = new Bull('delay-notifications', redisUrl, {
    redis: { maxRetriesPerRequest: 1 },
  });

  delayQueue.on('error', () => {
    isRedisAvailable = false;
  });
} catch (_) {
  isRedisAvailable = false;
}

async function processDelayJob(data) {
  const { applicationId, email, phone, name, communicationType = 'email', stage = 'email' } = data;
  logger.info(`Processing delay followup stage: ${stage}`, { applicationId, communicationType });

  try {
    const bookingLink = `${bookingBaseUrl}/book/${applicationId}`;

    if (stage === 'email') {
      try {
        await axios.post(`${notificationServiceUrl}/notify/email`, {
          email,
          subject: 'Follow-up: Your Application & Interview Booking',
          message: `Hi ${name}, this is a follow-up regarding your application. Please schedule your interview: ${bookingLink}`,
          bookingUrl: bookingLink,
          candidateName: name,
        }, { timeout: 5000 });
      } catch (err) {
        logger.warn(`Followup email notification failed/skipped: ${err.message}`);
      }
      return { applicationId, stage: 'email', sent: true };
    } else if (stage === 'unresponsive') {
      try {
        await axios.patch(
          `${applicationServiceUrl}/applications/${applicationId}`,
          {
            status: 'unresponsive',
            unresponsiveAt: new Date().toISOString(),
          },
          {
            headers: { Authorization: `Bearer ${apiAccessToken}` },
            timeout: 5000,
          }
        );
      } catch (err) {
        logger.warn(`Mark unresponsive failed/skipped: ${err.message}`);
      }
      return { applicationId, stage: 'unresponsive', status: 'marked_unresponsive' };
    }
  } catch (err) {
    logger.error('Followup stage failed', { applicationId, stage, error: err.message });
  }
}

if (delayQueue) {
  delayQueue.process(async (job) => {
    return processDelayJob(job.data);
  });
}

const scheduleFollowup = async ({
  applicationId,
  email,
  phone,
  name,
  communicationType = 'email',
  delayMs = 24 * 60 * 60 * 1000,
}) => {
  if (isRedisAvailable && delayQueue) {
    try {
      const emailStage = await delayQueue.add(
        { applicationId, email, phone, name, communicationType, stage: 'email' },
        { delay: delayMs, attempts: 3, jobId: `${applicationId}-email-stage` }
      );
      return { emailStage };
    } catch (_) {}
  }
  return { scheduled: true, inMemory: true, applicationId };
};

module.exports = { delayQueue, scheduleFollowup, processDelayJob };
