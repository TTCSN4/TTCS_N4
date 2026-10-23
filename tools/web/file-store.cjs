'use strict';
const fs = require('node:fs');
const path = require('node:path');

module.exports = async function createFileStore(api) {
  const file = path.resolve(process.env.DATA_FILE || path.join(__dirname, '../../data', 'web-state.json'));
  const lock = file + '.lock';
  fs.mkdirSync(path.dirname(file), { recursive: true });
  if (fs.existsSync(lock)) {
    const pid = Number(fs.readFileSync(lock, 'utf8'));
    if (!Number.isInteger(pid) || pid <= 0) throw Error('File khóa dữ liệu không hợp lệ: ' + lock);
    try { process.kill(pid, 0); throw Error('Dữ liệu đang được một tiến trình khác sử dụng (PID ' + pid + ').'); }
    catch (error) { if (error.code !== 'ESRCH') throw error; }
    fs.unlinkSync(lock);
  }
  fs.writeFileSync(lock, String(process.pid), { flag: 'wx', mode: 0o600 });
  let closed = false;
  function close() { if (!closed) { closed = true; fs.unlinkSync(lock); } }
  async function save(state) {
    const temp = file + '.' + process.pid + '.tmp';
    try {
      const descriptor = fs.openSync(temp, 'w', 0o600);
      try { fs.writeFileSync(descriptor, JSON.stringify(state)); fs.fsyncSync(descriptor); }
      finally { fs.closeSync(descriptor); }
      // Windows/OneDrive may briefly hold the destination open. Retry only transient locks.
      for (let attempt = 0; ; attempt++) {
        try { await fs.promises.rename(temp, file); break; }
        catch (error) {
          if (!['EPERM', 'EBUSY', 'EACCES'].includes(error.code) || attempt >= 4) throw error;
          await new Promise(resolve => setTimeout(resolve, 20 * 2 ** attempt));
        }
      }
    } finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
  }
  try {
    if (fs.existsSync(file)) {
      const saved = JSON.parse(fs.readFileSync(file, 'utf8'));
      if (!['rooms', 'users', 'equipment', 'meetings', 'equipmentBookings'].every(key => Array.isArray(saved[key]))) throw Error('Dữ liệu lưu không hợp lệ. Giữ nguyên file để kiểm tra: ' + file);
      api.restore(saved);
    }
    api.auth.initialize(api);
    await save(api.exportState());
  } catch (error) { close(); throw error; }
  process.once('exit', close);
  let tail = Promise.resolve();
  async function transaction(req, res, url) {
    const before = api.exportState();
    let status = 200, headers = {}, result = '';
    const capture = {
      writeHead(code, values) { status = code; Object.assign(headers, values); },
      setHeader(key, value) { headers[key] = value; },
      end(value) { result = value || ''; }
    };
    try {
      await api(req, capture, url);
      if (status >= 200 && status < 300) {
        const after = api.exportState();
        if (after.revision !== before.revision) await save(after);
        if (url.pathname === '/__portal/health' || url.pathname === '/api/demo/state') {
          const data = JSON.parse(result); data.storage = 'file'; result = JSON.stringify(data);
        }
      } else api.restore(before);
      res.writeHead(status, { ...headers, 'X-TTCS-Storage': 'file' });
      res.end(result);
    } catch (error) {
      api.restore(before);
      console.error('Local storage failed:', error.code || error.name);
      if (!res.headersSent) res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ message: 'Không lưu được dữ liệu. Thay đổi chưa được xác nhận.' }));
    }
  }
  return {
    handle(req, res, url) { const job = tail.then(() => transaction(req, res, url)); tail = job.catch(() => {}); return job; },
    close
  };
};
