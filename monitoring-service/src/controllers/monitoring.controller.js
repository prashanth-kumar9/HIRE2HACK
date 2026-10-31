const monitoringService = require('../services/monitoring.service');
const axios = require('axios');

const orchestrateCommunications = async (req, res, next) => {
  try {
    const { jobId, limit = 5000, smsRateLimit = 10 } = req.body;

    if (!jobId || typeof jobId !== 'string') {
      return res.status(400).json({ error: 'jobId (string) is required' });
    }

    const numLimit = Math.min(Number(limit), 5000);
    const numSmsRateLimit = Math.max(1, Number(smsRateLimit));

    console.log(`[ORCHESTRATE] Fetching ${numLimit} applications for jobId: ${jobId}`);

    try {
      const applicationServiceUrl = process.env.APPLICATION_SERVICE_URL || 'http://localhost:3002';

      const appRes = await axios.get(
        `${applicationServiceUrl}/applications/byjobid?jobId=${jobId}&limit=${numLimit}`
      );

      const applications = appRes.data.applications || [];

      if (applications.length === 0) {
        return res.status(200).json({
          jobId,
          message: 'No pending applications found',
          breakdown: { total: 0, sms: { count: 0 }, email: { count: 0 } }
        });
      }

      const smsApps = applications.filter(app => app.communicationType === 'sms');
      const emailApps = applications.filter(app => app.communicationType === 'email');

      console.log(`[ORCHESTRATE] SMS: ${smsApps.length}, Email: ${emailApps.length}`);

      const result = await monitoringService.orchestrateCommunications(
        jobId,
        { sms: smsApps, email: emailApps },
        { smsRateLimit: numSmsRateLimit }
      );

      res.status(202).json(result);
    } catch (appServiceError) {
      console.error('[ORCHESTRATE] Application service error:', appServiceError.message);
      return res.status(503).json({
        error: 'Could not reach application service',
        details: appServiceError.message
      });
    }
  } catch (err) {
    console.error('[ORCHESTRATE] Error:', err);
    next(err);
  }
};

module.exports = { orchestrateCommunications };
