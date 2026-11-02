/** FC 3 HTTP-event proxy. Credentials are read only from the function environment. */
const JSON_HEADERS = {
  'Content-Type': 'application/json; charset=utf-8',
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff',
};

class ProxyError extends Error {
  constructor(status, code, retryAfter = 0) {
    super(code);
    this.status = status;
    this.code = code;
    this.retryAfter = retryAfter;
  }
}

function integer(env, name, fallback, low, high) {
  const raw = env[name];
  if (raw === undefined || raw === '') return fallback;
  if (!/^\d+$/.test(raw) || +raw < low || +raw > high) throw new ProxyError(500, 'configuration_error');
  return +raw;
}

function configuration(env) {
  const host = (env.QWEATHER_HOST || '').trim().toLowerCase();
  const key = env.QWEATHER_KEY || '';
  if (host.length > 253 || !host.endsWith('.qweatherapi.com') || host.split('.').length < 3 ||
      !host.split('.').every(label => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)) ||
      !key || key.length > 512 || /[\r\n]/.test(key)) throw new ProxyError(500, 'configuration_error');
  const origins = (env.FC_ALLOWED_ORIGINS || '').split(',').map(v => v.trim()).filter(Boolean);
  if (origins.some(value => {
    try { const url = new URL(value); return url.origin !== value || url.protocol !== 'https:'; }
    catch { return true; }
  })) throw new ProxyError(500, 'configuration_error');
  return {
    host, key, origins,
    timeout: integer(env, 'FC_FETCH_TIMEOUT_MS', 8000, 100, 15000),
    maxBody: integer(env, 'FC_MAX_RESPONSE_BYTES', 1048576, 1024, 2097152),
    maxEntries: integer(env, 'FC_CACHE_ENTRIES', 256, 1, 1024),
    maxBytes: integer(env, 'FC_CACHE_BYTES', 8388608, 1024, 33554432),
    peerRpm: integer(env, 'FC_PEER_RPM', 60, 1, 10000),
    instanceRpm: integer(env, 'FC_INSTANCE_RPM', 600, 1, 10000),
    upstreamRpm: integer(env, 'FC_UPSTREAM_RPM', 180, 1, 10000),
  };
}

function parseEvent(event) {
  try {
    if (typeof event === 'string' || Buffer.isBuffer(event)) {
      if (Buffer.byteLength(event) > 32768) throw new Error();
      event = JSON.parse(event.toString());
    }
    if (!event || typeof event !== 'object' || Array.isArray(event)) throw new Error();
    return event;
  } catch { throw new ProxyError(400, 'invalid_event'); }
}

function coordinate(text, min, max) {
  if (!/^-?\d{1,3}(?:\.\d{1,2})?$/.test(text) || +text < min || +text > max)
    throw new ProxyError(400, 'invalid_coordinates');
  return Number(text).toFixed(2);
}

function location(value) {
  if (value.includes(',')) {
    const pair = value.split(',');
    if (pair.length !== 2) throw new ProxyError(400, 'invalid_location');
    return `${coordinate(pair[0], -180, 180)},${coordinate(pair[1], -90, 90)}`;
  }
  if (!/^[A-Za-z0-9]{3,20}$/.test(value)) throw new ProxyError(400, 'invalid_location');
  return value;
}

/** Return a canonical URL path and a bounded, sorted query; never join caller-provided URLs. */
function route(request) {
  const http = request.requestContext?.http;
  let path = http?.path ?? request.rawPath ?? request.path;
  if (typeof path !== 'string' || path.length > 160 || /[%\\?#\u0000-\u0020]/.test(path))
    throw new ProxyError(400, 'invalid_path');
  const query = request.queryParameters ?? request.queryStringParameters ?? {};
  if (!query || typeof query !== 'object' || Array.isArray(query)) throw new ProxyError(400, 'invalid_query');
  let allowed, required = [], ttl;
  if (path === '/geo/v2/city/lookup') {
    allowed = ['location', 'lang', 'number', 'adm', 'range']; required = ['location']; ttl = 86400000;
  } else if (/^\/v7\/weather\/(now|24h|7d)$/.test(path)) {
    allowed = ['location', 'lang', 'unit']; required = ['location'];
    ttl = path.endsWith('/now') ? 120000 : path.endsWith('/24h') ? 600000 : 1800000;
  } else if (path === '/v7/indices/1d') {
    allowed = ['location', 'lang', 'type']; required = ['location', 'type']; ttl = 3600000;
  } else {
    const match = path.match(/^\/(weather\/v1\/(current|hourly|daily)|airquality\/v1\/current|weatheralert\/v1\/current)\/(-?[\d.]+)\/(-?[\d.]+)$/);
    if (!match) throw new ProxyError(404, 'route_not_found');
    path = `/${match[1]}/${coordinate(match[3], -90, 90)}/${coordinate(match[4], -180, 180)}`;
    allowed = ['lang'];
    if (match[2]) allowed.push('localTime');
    if (match[2] === 'hourly') allowed.push('hours');
    if (match[2] === 'daily') allowed.push('days');
    ttl = match[1].startsWith('weatheralert') ? 0 : match[2] === 'daily' ? 1800000 :
      match[2] === 'hourly' || match[1].startsWith('airquality') ? 600000 : 120000;
  }
  const pairs = [];
  for (const [name, input] of Object.entries(query)) {
    if (!allowed.includes(name) || typeof input !== 'string' || input.length > 100 || /[\u0000-\u001f\u007f]/.test(input))
      throw new ProxyError(400, 'invalid_query');
    let value = input.trim();
    if (!value) throw new ProxyError(400, 'invalid_query');
    if (name === 'lang' && !/^[a-z]{2}(?:-[A-Za-z]{2,8})?$/.test(value)) throw new ProxyError(400, 'invalid_query');
    if (name === 'unit' && value !== 'm') throw new ProxyError(400, 'metric_units_required');
    if (name === 'localTime' && value !== 'true') throw new ProxyError(400, 'local_time_required');
    if (name === 'type' && value !== '0') throw new ProxyError(400, 'all_indices_required');
    if (name === 'hours' && (!/^\d+$/.test(value) || +value < 1 || +value > 24)) throw new ProxyError(400, 'invalid_query');
    if (name === 'days' && (!/^\d+$/.test(value) || +value < 1 || +value > 7)) throw new ProxyError(400, 'invalid_query');
    if (name === 'number' && (!/^\d+$/.test(value) || +value < 1 || +value > 20)) throw new ProxyError(400, 'invalid_query');
    if (['adm', 'range'].includes(name) && !/^[\p{L}\p{N} .-]{1,40}$/u.test(value)) throw new ProxyError(400, 'invalid_query');
    if (name === 'location') {
      if (path.startsWith('/geo/')) {
        if (value.length > 80 || /[<>\/\\?#=&]/.test(value)) throw new ProxyError(400, 'invalid_location');
        if (value.includes(',')) value = location(value);
      } else value = location(value);
    }
    pairs.push([name, value]);
  }
  if (required.some(name => !pairs.some(pair => pair[0] === name))) throw new ProxyError(400, 'missing_query');
  pairs.sort(([a], [b]) => a.localeCompare(b));
  const encoded = new URLSearchParams(pairs).toString();
  return { path: path + (encoded ? `?${encoded}` : ''), ttl };
}

function retrySeconds(value, now) {
  if (!value) return 30;
  const delay = /^\d+$/.test(value) ? Number(value) : Math.ceil((Date.parse(value) - now) / 1000);
  return Number.isFinite(delay) ? Math.min(3600, Math.max(1, delay)) : 30;
}

function hasExpectedPayload(object, path) {
  if (String(object.code) === '204') return true;
  if (path.startsWith('/geo/')) return Array.isArray(object.location);
  if (path.startsWith('/v7/weather/now')) return object.now && typeof object.now === 'object';
  if (path.startsWith('/v7/weather/24h')) return Array.isArray(object.hourly);
  if (path.startsWith('/v7/weather/7d') || path.startsWith('/v7/indices/')) return Array.isArray(object.daily);
  if (path.startsWith('/weather/v1/current/')) return object.temperature && typeof object.temperature === 'object';
  if (path.startsWith('/weather/v1/hourly/')) return Array.isArray(object.hours);
  if (path.startsWith('/weather/v1/daily/')) return Array.isArray(object.days);
  if (path.startsWith('/airquality/')) return Array.isArray(object.indexes);
  return Array.isArray(object.alerts) || object.metadata?.zeroResult === true;
}

async function readBody(response, limit) {
  if (Number(response.headers.get('content-length')) > limit) throw new ProxyError(502, 'upstream_response_too_large');
  if (!response.body) throw new ProxyError(502, 'invalid_upstream_response');
  const reader = response.body.getReader();
  const parts = [];
  let length = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      length += chunk.value.byteLength;
      if (length > limit) { await reader.cancel(); throw new ProxyError(502, 'upstream_response_too_large'); }
      parts.push(chunk.value);
    }
  } finally { reader.releaseLock(); }
  try {
    const object = JSON.parse(Buffer.concat(parts).toString('utf8'));
    if (!object || typeof object !== 'object' || Array.isArray(object)) throw new Error();
    return object;
  } catch { throw new ProxyError(502, 'invalid_upstream_response'); }
}

/** Dependencies are injectable only for isolated tests; production exports the environment-backed handler below. */
export function createHandler({ env = process.env, fetchImpl = globalThis.fetch, now = Date.now } = {}) {
  const cache = new Map();
  const pending = new Map();
  const failures = new Map();
  const peers = new Map();
  let bytes = 0, windowAt = 0, instanceRequests = 0, upstreamRequests = 0, upstreamNotBefore = 0;

  function count(config, request) {
    const time = now();
    if (time - windowAt >= 60000) { windowAt = time; instanceRequests = 0; upstreamRequests = 0; peers.clear(); }
    const peer = String(request.requestContext?.http?.sourceIp || 'unknown').slice(0, 80);
    if (++instanceRequests > config.instanceRpm || (peers.get(peer) || 0) >= config.peerRpm || (!peers.has(peer) && peers.size >= 4096))
      throw new ProxyError(429, 'instance_rate_limited', Math.max(1, Math.ceil((windowAt + 60000 - time) / 1000)));
    peers.set(peer, (peers.get(peer) || 0) + 1);
  }

  async function load(config, selection) {
    if (now() < upstreamNotBefore) throw new ProxyError(429, 'upstream_rate_limited', Math.ceil((upstreamNotBefore - now()) / 1000));
    if (++upstreamRequests > config.upstreamRpm) throw new ProxyError(429, 'instance_upstream_budget', Math.max(1, Math.ceil((windowAt + 60000 - now()) / 1000)));
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), config.timeout);
    try {
      const response = await fetchImpl(`https://${config.host}${selection.path}`, {
        method: 'GET', headers: { 'X-QW-Api-Key': config.key, Accept: 'application/json' },
        redirect: 'manual', signal: controller.signal,
      });
      if (response.status >= 300 && response.status < 400) {
        await response.body?.cancel();
        throw new ProxyError(502, 'upstream_redirect_rejected');
      }
      if (!response.ok) {
        await response.body?.cancel();
        const retry = response.status === 429 ? retrySeconds(response.headers.get('retry-after'), now()) : 0;
        if (retry) upstreamNotBefore = now() + retry * 1000;
        throw new ProxyError(response.status >= 400 && response.status <= 599 ? response.status : 502,
          response.status === 429 ? 'upstream_rate_limited' : 'upstream_rejected', retry);
      }
      const object = await readBody(response, config.maxBody);
      const code = String(object.code ?? '200');
      if (code !== '200' && code !== '204') {
        const status = /^\d{3}$/.test(code) && +code >= 400 && +code <= 599 ? +code : 502;
        if (status === 429) upstreamNotBefore = now() + 30000;
        throw new ProxyError(status, 'upstream_rejected', status === 429 ? 30 : 0);
      }
      if (!hasExpectedPayload(object, selection.path)) throw new ProxyError(502, 'invalid_upstream_response');
      return JSON.stringify(object);
    } catch (error) {
      const timedOut = controller.signal.aborted;
      controller.abort();
      if (error instanceof ProxyError) throw error;
      throw new ProxyError(timedOut ? 504 : 502, 'upstream_unavailable');
    } finally { clearTimeout(timeout); }
  }

  return async function handler(event) {
    let cors = {};
    try {
      const request = parseEvent(event);
      const config = configuration(env);
      const origin = Object.entries(request.headers || {}).find(([key]) => key.toLowerCase() === 'origin')?.[1];
      if (origin && config.origins.includes(origin)) cors = { 'Access-Control-Allow-Origin': origin, Vary: 'Origin' };
      const method = request.requestContext?.http?.method ?? request.httpMethod ?? request.method ?? 'GET';
      const selection = route(request);
      if (method === 'OPTIONS') {
        if (!origin || !config.origins.includes(origin)) throw new ProxyError(403, 'origin_not_allowed');
        return { statusCode: 204, headers: { ...JSON_HEADERS, ...cors, 'Access-Control-Allow-Methods': 'GET, OPTIONS', 'Access-Control-Max-Age': '600' }, body: '', isBase64Encoded: false };
      }
      if (method !== 'GET') throw new ProxyError(405, 'method_not_allowed');
      count(config, request);
      const key = config.host + selection.path;
      const entry = cache.get(key);
      let body, age = 0;
      if (entry && entry.until > now()) {
        cache.delete(key); cache.set(key, entry); body = entry.body;
        age = Math.max(0, Math.floor((now() - entry.savedAt) / 1000));
      } else {
        if (entry) { bytes -= entry.size; cache.delete(key); }
        const failure = failures.get(key);
        if (failure && failure.until > now()) throw failure.error;
        if (pending.has(key)) body = await pending.get(key);
        else {
          if (pending.size >= 8) throw new ProxyError(503, 'instance_busy', 2);
          const flight = load(config, selection);
          pending.set(key, flight);
          try {
            body = await flight;
            if (selection.ttl > 0) {
              const size = Buffer.byteLength(body);
              if (size <= config.maxBytes) {
                cache.set(key, { body, size, savedAt: now(), until: now() + selection.ttl }); bytes += size;
                while (cache.size > config.maxEntries || bytes > config.maxBytes) {
                  const oldest = cache.keys().next().value; bytes -= cache.get(oldest).size; cache.delete(oldest);
                }
              }
            }
          } catch (error) {
            failures.delete(key); failures.set(key, { error, until: now() + 10000 });
            while (failures.size > config.maxEntries) failures.delete(failures.keys().next().value);
            throw error;
          } finally { pending.delete(key); }
        }
      }
      return { statusCode: 200, headers: { ...JSON_HEADERS, ...cors, Age: String(age) }, body, isBase64Encoded: false };
    } catch (error) {
      const known = error instanceof ProxyError ? error : new ProxyError(500, 'internal_error');
      return { statusCode: known.status, headers: { ...JSON_HEADERS, ...cors,
        ...(known.status === 405 ? { Allow: 'GET, OPTIONS' } : {}),
        ...(known.retryAfter ? { 'Retry-After': String(known.retryAfter) } : {}),
      }, body: JSON.stringify({ error: known.code }), isBase64Encoded: false };
    }
  };
}

export const handler = createHandler();
