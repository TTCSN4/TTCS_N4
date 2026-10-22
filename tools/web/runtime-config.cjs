'use strict';
const fs = require('node:fs');
const path = require('node:path');
module.exports = function configure() {
  const file = path.join(__dirname, '../../site.config.json');
  const config = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
  process.env.DEMO_MODE ??= (process.env.APP_MODE || config.mode || 'local') === 'proxy' ? '0' : '1';
  process.env.DATA_STORE ||= config.storage || 'file';
  process.env.PORT ||= String(config.port || 8090);
  process.env.BACKEND_URL ||= config.backendUrl || 'http://127.0.0.1:8080';
  if (!['file', 'mysql', 'memory'].includes(process.env.DATA_STORE)) throw Error('DATA_STORE phải là file, mysql hoặc memory.');
  const port = Number(process.env.PORT);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw Error('PORT không hợp lệ.');
};
