const { Router } = require('express');
const controller = require('../controllers/monitoring.controller');

const router = Router();

router.post('/orchestrate', controller.orchestrateCommunications);

module.exports = router;
