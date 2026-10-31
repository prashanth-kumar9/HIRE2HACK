const Bull = require('bull');
const axios = require('axios');
const Redis = require('ioredis');
const { redisUrl, applicationServiceUrl, notificationServiceUrl } = require('../config/vars');
const logger = require('../config/logger');

const applicationTriggerQueue = new Bull('application-trigger', redisUrl);
const redis = new Redis(redisUrl);

applicationTriggerQueue.process(async (job) => {
  const { applicationId, communicationType, name, email, phone, jobId } = job.data;
  const type = communicationType || 'email';

  logger.info(`Processing ${type} trigger job`, { applicationId, type });

  try {
    if (type === 'sms') {
      await axios.post(`${notificationServiceUrl}/notify/sms`, {
        phone,
        message: `Hi ${name}, we have received your application. Click here to schedule your voice interview: https://booking.example.com/${applicationId}`
      });

      await axios.patch(`${applicationServiceUrl}/applications/${applicationId}`, {
        status: 'sms_sent',
        lastCommunicationAt: new Date(),
        jobId
      });

      // Set Redis key to track pending application for 48h (until final followup)
      await redis.setex(`pending:${applicationId}`, 48 * 60 * 60, 'sms_sent');
      logger.info(`SMS sent and pending tracked for 48h`, { applicationId });

      return { applicationId, type: 'sms', status: 'sms_sent', tracked: true };
    } else if (type === 'email') {
      await axios.post(`${notificationServiceUrl}/notify/email`, {
        email,
        subject: 'Your Application - Schedule Your Interview',
        message: `Hi ${name}, thank you for applying. Please schedule your voice interview: https://booking.example.com/${applicationId}`
      });

      await axios.patch(`${applicationServiceUrl}/applications/${applicationId}`, {
        status: 'email_sent',
        lastCommunicationAt: new Date(),
        jobId
      });

      logger.info(`Email sent`, { applicationId });
      return { applicationId, type: 'email', status: 'email_sent' };
    } else {
      throw new Error(`Unknown communicationType: ${type}`);
    }
  } catch (err) {
    logger.error(`Application trigger job failed`, { applicationId, type, error: err.message });
    throw err;
  }
});

applicationTriggerQueue.on('failed', (job, err) => {
  logger.error(`Job failed`, { jobId: job.id, applicationId: job.data.applicationId, error: err.message });
});

applicationTriggerQueue.on('completed', (job, result) => {
  logger.info(`Job completed`, { jobId: job.id, applicationId: job.data.applicationId });
});

module.exports = applicationTriggerQueue;
