require('dotenv').config();
const app = require('./config/express');
require('./queues/applicationTrigger.queue');
require('./queues/delay.queue');
const { port, env } = require('./config/vars');
const logger = require('./config/logger');

app.listen(port, () => {
  logger.info(`monitoring-service running on port ${port} [${env}]`);
});
