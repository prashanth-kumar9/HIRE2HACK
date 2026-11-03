const { addTriggerJob } = require('../queues/applicationTrigger.queue');
const { scheduleFollowup } = require('../queues/delay.queue');
const logger = require('../config/logger');

const orchestrateCommunications = async (jobId, appsByType, config = {}) => {
  const { sms = [], email = [] } = appsByType;
  const { smsRateLimit = 10 } = config;

  await queueSmsCommunications(jobId, sms, smsRateLimit);
  await queueEmailCommunications(jobId, email);

  console.log(`[ORCHESTRATE] Completed for jobId: ${jobId}`);

  return { success: true, total: sms.length + email.length, smsCount: sms.length, emailCount: email.length };
};

const queueSmsCommunications = async (jobId, applications, smsRateLimit = 10) => {
  const delayBetweenSMS = Math.ceil(1000 / smsRateLimit);

  console.log(`[SMS-QUEUE] Queuing ${applications.length} with ${delayBetweenSMS}ms delay (${smsRateLimit}/sec)`);

  for (let i = 0; i < applications.length; i++) {
    const app = applications[i];
    try {
      const delayMs = i * delayBetweenSMS;

      await addTriggerJob(
        {
          applicationId: app._id || app.applicationId,
          jobId,
          name: app.name,
          email: app.email,
          phone: app.phone,
          communicationType: 'sms',
        },
        {
          jobId: `${jobId}-sms-${app._id || app.applicationId}`,
          attempts: 3,
          backoff: { type: 'exponential', delay: 1000 },
          removeOnComplete: true,
          delay: delayMs,
        }
      );

      await scheduleFollowup({
        applicationId: app._id || app.applicationId,
        email: app.email,
        phone: app.phone,
        name: app.name,
        communicationType: 'sms',
        delayMs: 24 * 60 * 60 * 1000,
      }).catch(() => {});
    } catch (error) {
      logger.error('[SMS-QUEUE] Failed', { applicationId: app._id || app.applicationId });
    }
  }
};

const queueEmailCommunications = async (jobId, applications) => {
  console.log(`[EMAIL-QUEUE] Queuing ${applications.length}`);

  for (const app of applications) {
    try {
      await addTriggerJob(
        {
          applicationId: app._id || app.applicationId,
          jobId,
          name: app.name,
          email: app.email,
          phone: app.phone,
          communicationType: 'email',
        },
        {
          jobId: `${jobId}-email-${app._id || app.applicationId}`,
          attempts: 3,
          backoff: { type: 'exponential', delay: 1000 },
          removeOnComplete: true,
          delay: 0,
        }
      );

      await scheduleFollowup({
        applicationId: app._id || app.applicationId,
        email: app.email,
        phone: app.phone,
        name: app.name,
        communicationType: 'email',
        delayMs: 48 * 60 * 60 * 1000,
      }).catch(() => {});
    } catch (error) {
      logger.error('[EMAIL-QUEUE] Failed', { applicationId: app._id || app.applicationId });
    }
  }
};

module.exports = { orchestrateCommunications };
