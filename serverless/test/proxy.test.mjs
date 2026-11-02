import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from '../index.mjs';

const environment = { QWEATHER_KEY: 'test-only-not-a-credential', QWEATHER_HOST: 'example.xy.qweatherapi.com' };
const event = (path = '/v7/weather/now', query = { location: '101010100' }, extra = {}) => ({
  rawPath: path, queryParameters: query,
  requestContext: { http: { method: 'GET', path, sourceIp: '192.0.2.1' } }, ...extra,
});
const payload = () => new Response(JSON.stringify({ code: '200', now: { temp: '13' }, location: [],
  temperature: { value: 13, unit: '°C' }, hours: [], days: [], hourly: [], daily: [], indexes: [], alerts: [] }), {
  headers: { 'Content-Type': 'application/json' },
});

test('FC Buffer and object events use canonical allowlisted routes and a server-only key', async () => {
  let target, options;
  const proxy = createHandler({ env: environment, fetchImpl: async (url, init) => {
    target = url; options = init; return payload();
  } });
  const result = await proxy(Buffer.from(JSON.stringify(event())));
  assert.equal(result.statusCode, 200);
  assert.equal(target, 'https://example.xy.qweatherapi.com/v7/weather/now?location=101010100');
  assert.equal(options.headers['X-QW-Api-Key'], environment.QWEATHER_KEY);
  assert.equal(options.redirect, 'manual');
  assert.ok(!result.body.includes(environment.QWEATHER_KEY));
  assert.equal(result.headers['Access-Control-Allow-Origin'], undefined);
});

test('legacy event query names and coordinate routes are accepted without double encoding', async () => {
  const urls = [];
  const proxy = createHandler({ env: environment, fetchImpl: async url => { urls.push(url); return payload(); } });
  const result = await proxy(JSON.stringify({ path: '/geo/v2/city/lookup', httpMethod: 'GET', queryStringParameters: { location: '沙坪坝', lang: 'zh' } }));
  assert.equal(result.statusCode, 200);
  assert.equal(new URL(urls[0]).searchParams.get('location'), '沙坪坝');
  assert.equal((await proxy(event('/weather/v1/hourly/39.9/116.41', { hours: '24', localTime: 'true', lang: 'zh' }))).statusCode, 200);
  assert.match(urls[1], /\/weather\/v1\/hourly\/39\.90\/116\.41\?/);
});

test('bad configuration never contacts a caller-controlled host', async () => {
  for (const host of ['https://example.xy.qweatherapi.com', 'example.xy.qweatherapi.com/extra', 'example.com', 'example.xy.qweatherapi.com:443', 'qweatherapi.com', 'example.xy.qweatherapi.com@evil.test']) {
    let called = false;
    const proxy = createHandler({ env: { ...environment, QWEATHER_HOST: host }, fetchImpl: async () => { called = true; return payload(); } });
    const result = await proxy(event());
    assert.equal(result.statusCode, 500);
    assert.equal(called, false);
    assert.equal(result.body, '{"error":"configuration_error"}');
  }
});

test('unknown routes, path traversal, unsupported parameters and bad coordinates fail before fetch', async () => {
  let calls = 0;
  const proxy = createHandler({ env: environment, fetchImpl: async () => { calls++; return payload(); } });
  const invalid = [event('/v7/weather/now', { location: '101010100', key: 'not-accepted' }),
    event('//evil.test'), event('/weather/v1/current/91/116', {}), event('/weather/v1/current/39/181', {}),
    event('/v7/weather/../now'), event('/v7/weather/%6eow'), event('/v7/weather/now', { location: ['101010100'] }),
    event('/v7/indices/1d', { location: '101010100', type: '1,2,3' }), event('/weather/v1/hourly/39/116', { hours: '240' })];
  for (const request of invalid) assert.ok((await proxy(request)).statusCode >= 400);
  assert.equal((await proxy('not json')).statusCode, 400);
  assert.equal(calls, 0);
});

test('only GET forwards, and OPTIONS requires an explicitly allowed HTTPS origin', async () => {
  let calls = 0;
  const proxy = createHandler({ env: { ...environment, FC_ALLOWED_ORIGINS: 'https://weather.example.com' }, fetchImpl: async () => { calls++; return payload(); } });
  const request = event();
  request.requestContext.http.method = 'POST';
  assert.equal((await proxy(request)).statusCode, 405);
  request.requestContext.http.method = 'OPTIONS';
  request.headers = { Origin: 'https://weather.example.com' };
  const result = await proxy(request);
  assert.equal(result.statusCode, 204);
  assert.equal(result.headers['Access-Control-Allow-Origin'], 'https://weather.example.com');
  request.headers.Origin = 'https://evil.example.com';
  assert.equal((await proxy(request)).statusCode, 403);
  assert.equal(calls, 0);
});

test('HTTP errors and JSON business errors are not disguised as successful weather', async () => {
  for (const status of [401, 403, 404, 500, 503]) {
    const proxy = createHandler({ env: environment, fetchImpl: async () => new Response('private upstream error', { status }) });
    const result = await proxy(event());
    assert.equal(result.statusCode, status);
    assert.ok(!result.body.includes('private'));
  }
  const proxy = createHandler({ env: environment, fetchImpl: async () => new Response('{"code":"403","message":"secret internal detail"}') });
  const result = await proxy(event());
  assert.equal(result.statusCode, 403);
  assert.ok(!result.body.includes('secret'));
});

test('429 honors Retry-After and stops different upstream paths during the cooldown', async () => {
  let calls = 0, time = 100000;
  const proxy = createHandler({ env: environment, now: () => time, fetchImpl: async () => {
    calls++; return new Response('limited', { status: 429, headers: { 'Retry-After': '45' } });
  } });
  const first = await proxy(event());
  assert.equal(first.statusCode, 429);
  assert.equal(first.headers['Retry-After'], '45');
  time += 2000;
  const second = await proxy(event('/v7/weather/7d'));
  assert.equal(second.statusCode, 429);
  assert.equal(second.headers['Retry-After'], '43');
  assert.equal(calls, 1);
});

test('redirects, invalid/oversized JSON and transport errors stay generic', async () => {
  for (const response of [new Response('go', { status: 302, headers: { Location: 'https://evil.test' } }),
    new Response('not json'), new Response('x'.repeat(2000)), new Response('[]'), new Response('{"message":"not weather"}')]) {
    const proxy = createHandler({ env: { ...environment, FC_MAX_RESPONSE_BYTES: '1024' }, fetchImpl: async () => response });
    assert.equal((await proxy(event())).statusCode, 502);
  }
  const proxy = createHandler({ env: environment, fetchImpl: async () => { throw new Error('private-host and key'); } });
  assert.deepEqual(JSON.parse((await proxy(event())).body), { error: 'upstream_unavailable' });
  assert.equal((await createHandler({ env: environment, fetchImpl: async () => { throw new Error(); } })(event())).statusCode, 502);
});

test('timeout aborts the upstream call', async () => {
  const proxy = createHandler({ env: { ...environment, FC_FETCH_TIMEOUT_MS: '100' }, fetchImpl: async (_url, { signal }) =>
    new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true })) });
  assert.equal((await proxy(event())).statusCode, 504);
});

test('bounded cache and single flight share current weather but alerts are never cached', async () => {
  let calls = 0, release;
  const gate = new Promise(resolve => { release = resolve; });
  const proxy = createHandler({ env: { ...environment, FC_CACHE_ENTRIES: '1' }, fetchImpl: async () => { calls++; await gate; return payload(); } });
  const first = proxy(event());
  const second = proxy(event());
  release();
  assert.equal((await first).statusCode, 200);
  assert.equal((await second).statusCode, 200);
  assert.equal(calls, 1);
  await proxy(event());
  assert.equal(calls, 1);
  await proxy(event('/v7/weather/now', { location: '101020100' }));
  await proxy(event());
  assert.equal(calls, 3);
  await proxy(event('/weatheralert/v1/current/39.90/116.41', {}));
  await proxy(event('/weatheralert/v1/current/39.90/116.41', {}));
  assert.equal(calls, 5);
});

test('instance request and upstream budgets apply before additional upstream work', async () => {
  let calls = 0;
  const proxy = createHandler({ env: { ...environment, FC_PEER_RPM: '1' }, fetchImpl: async () => { calls++; return payload(); } });
  assert.equal((await proxy(event())).statusCode, 200);
  assert.equal((await proxy(event())).statusCode, 429);
  assert.equal(calls, 1);
  const limited = createHandler({ env: { ...environment, FC_UPSTREAM_RPM: '1' }, fetchImpl: async () => payload() });
  await limited(event());
  assert.equal((await limited(event('/v7/weather/7d'))).statusCode, 429);
});

test('cached responses carry their real instance-cache age and expire instead of serving stale', async () => {
  let calls = 0, time = 100000;
  const proxy = createHandler({ env: environment, now: () => time, fetchImpl: async () => { calls++; return payload(); } });
  assert.equal((await proxy(event())).headers.Age, '0');
  time += 25000;
  assert.equal((await proxy(event())).headers.Age, '25');
  assert.equal(calls, 1);
  time += 120000;
  assert.equal((await proxy(event())).headers.Age, '0');
  assert.equal(calls, 2);
});
