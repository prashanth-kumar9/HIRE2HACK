const { Router } = require('express');
const controller = require('../controllers/monitoring.controller');

const router = Router();

router.get('/health', (_req, res) => res.json({ status: 'healthy', service: 'monitoring-service' }));
router.post('/orchestrate', controller.orchestrateCommunications);

module.exports = router;
