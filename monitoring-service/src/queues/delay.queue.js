const Bull = require('bull');
const axios = require('axios');
const Redis = require('ioredis');
const { redisUrl, applicationServiceUrl, notificationServiceUrl } = require('../config/vars');
const logger = require('../config/logger');

const delayQueue = new Bull('delay-notifications', redisUrl);
const redis = new Redis(redisUrl);

delayQueue.process(async (job) => {
  const { applicationId, email, phone, name, communicationType = 'email', stage = 'email' } = job.data;

  logger.info(`Processing stage: ${stage}`, { applicationId, communicationType });

  try {
    // Check if application is still pending (not booked)
    const isPending = await redis.exists(`pending:${applicationId}`);

    if (!isPending) {
      logger.info(`Application already booked, skipping stage: ${stage}`, { applicationId });
      return { applicationId, status: 'booked', skipped: true };
    }

    // Stage 1: Send email at 24h
    if (stage === 'email') {
      await axios.post(`${notificationServiceUrl}/notify/email`, {
        email,
        subject: 'Follow-up: Your Application',
        message: `Hi ${name}, this is a follow-up regarding your application. Please reach out if you have questions: https://booking.example.com/${applicationId}`
      });

      logger.info(`Email followup sent`, { applicationId });
      return { applicationId, stage: 'email', sent: true };
    }

    // Stage 2: Mark unresponsive at 48h
    else if (stage === 'unresponsive') {
      // Check one final time if booked
      const stillPending = await redis.exists(`pending:${applicationId}`);

      if (!stillPending) {
        logger.info(`Application booked before unresponsive check`, { applicationId });
        return { applicationId, stage: 'unresponsive', status: 'booked', skipped: true };
      }

      // Update application status to 'unresponsive'
      await axios.patch(`${applicationServiceUrl}/applications/${applicationId}`, {
        status: 'unresponsive',
        unresponsiveAt: new Date()
      });

      // Remove Redis key - stop any further triggers
      await redis.del(`pending:${applicationId}`);

      logger.info(`Application marked as unresponsive and removed from pending`, { applicationId });
      return { applicationId, stage: 'unresponsive', status: 'marked_unresponsive' };
    }

    throw new Error(`Unknown stage: ${stage}`);
  } catch (err) {
    logger.error(`Followup stage failed`, { applicationId, stage, error: err.message });
    throw err;
  }
});

const scheduleFollowup = async ({
  applicationId,
  email,
  phone,
  name,
  communicationType = 'email',
  delayMs = 24 * 60 * 60 * 1000
}) => {
  // Schedule stage 1: Send email at 24h
  const emailStage = await delayQueue.add(
    { applicationId, email, phone, name, communicationType, stage: 'email' },
    { delay: 24 * 60 * 60 * 1000, attempts: 3, backoff: { type: 'exponential', delay: 5000 }, jobId: `${applicationId}-email-stage` }
  );

  // Schedule stage 2: Mark unresponsive at 48h
  const unresponsiveStage = await delayQueue.add(
    { applicationId, email, phone, name, communicationType, stage: 'unresponsive' },
    { delay: 48 * 60 * 60 * 1000, attempts: 3, backoff: { type: 'exponential', delay: 5000 }, jobId: `${applicationId}-unresponsive-stage` }
  );

  return { emailStage, unresponsiveStage };
};

module.exports = { delayQueue, scheduleFollowup };
