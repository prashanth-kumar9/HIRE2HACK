require('dotenv').config();

module.exports = {
  port: process.env.PORT || 3003,
  env: process.env.NODE_ENV || 'development',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  applicationServiceUrl: process.env.APPLICATION_SERVICE_URL || 'http://localhost:3002',
  notificationServiceUrl: process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:3004',
  jwtSecret: process.env.JWT_SECRET || 'default_secret_change_me',
};
