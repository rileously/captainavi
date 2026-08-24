import app from './index';

declare const process: { exit: (code: number) => void };

async function runTests() {
  console.log('--- Starting Captain Avi Relay Server Unit Tests ---');
  let passed = 0;
  let failed = 0;

  function assert(condition: boolean, name: string) {
    if (condition) {
      console.log(`✓ PASS: ${name}`);
      passed++;
    } else {
      console.error(`✗ FAIL: ${name}`);
      failed++;
    }
  }

  // 1. Health check test
  const healthRes = await app.request('/health');
  assert(healthRes.status === 200, 'Health endpoint returns 200');
  const healthData = await healthRes.json() as { status: string };
  assert(healthData.status === 'ok', 'Health status is ok');

  // 2. Test Authorization check when API_SECRET_KEY is configured
  const mockEnv = {
    TELEGRAM_BOT_TOKEN: 'mock_token',
    TELEGRAM_CHAT_ID: 'mock_chat_id',
    API_SECRET_KEY: 'secret_123'
  };

  const unauthRes = await app.request('/api/trip/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tripId: 't1', latitude: 4.17, longitude: 73.5, batteryPct: 90, timestamp: Date.now() })
  }, mockEnv);
  assert(unauthRes.status === 401, 'Unauthorized request without Bearer token is rejected with 401');

  const authHeaderReq = await app.request('/api/trip/start', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer wrong_secret'
    },
    body: JSON.stringify({ tripId: 't1', latitude: 4.17, longitude: 73.5, batteryPct: 90, timestamp: Date.now() })
  }, mockEnv);
  assert(authHeaderReq.status === 401, 'Invalid Bearer token is rejected with 401');

  // 3. Trip status sends once, then edits the same Telegram message with inline URL buttons.
  const originalFetch = globalThis.fetch;
  const telegramCalls: Array<{ url: string; body: Record<string, unknown> }> = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>;
    telegramCalls.push({ url: String(input), body });
    return new Response(JSON.stringify({ ok: true, result: { message_id: 42 } }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }) as typeof fetch;

  try {
    const startedAt = Date.now();
    const startRes = await app.request('/api/trip/start', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer secret_123',
      },
      body: JSON.stringify({
        tripId: 'trip-1',
        captainName: 'Captain',
        latitude: 4.17,
        longitude: 73.5,
        batteryPct: 90,
        timestamp: startedAt,
      }),
    }, mockEnv);
    const startData = await startRes.json() as { messageId?: number };
    assert(startRes.status === 200 && startData.messageId === 42, 'Trip start returns Telegram status message ID');
    assert(telegramCalls[0]?.url.endsWith('/sendMessage') === true, 'Trip start creates one Telegram status message');
    const startMarkup = telegramCalls[0]?.body.reply_markup as { inline_keyboard?: unknown[][] } | undefined;
    assert(startMarkup?.inline_keyboard?.[0]?.length === 2, 'Trip status includes map and directions inline buttons');
    assert(startMarkup?.inline_keyboard?.[1]?.length === 1, 'Trip status includes a share-position inline button');

    telegramCalls.length = 0;
    const updateRes = await app.request('/api/trip/update', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer secret_123',
      },
      body: JSON.stringify({
        tripId: 'trip-1',
        captainName: 'Captain',
        latitude: 4.18,
        longitude: 73.51,
        speedKnots: 8.2,
        headingDegrees: 15,
        headingCardinal: 'NNE',
        batteryPct: 88,
        timestamp: startedAt + 600_000,
        statusMessageId: 42,
      }),
    }, mockEnv);
    assert(updateRes.status === 200, 'Live trip update returns 200');
    assert(telegramCalls.length === 1 && telegramCalls[0].url.endsWith('/editMessageText'), 'Live update edits instead of sending another message');
    assert(telegramCalls[0]?.body.message_id === 42, 'Live update edits the stored trip status message ID');
  } finally {
    globalThis.fetch = originalFetch;
  }

  console.log(`\nTests Completed: ${passed} Passed, ${failed} Failed`);
  if (failed > 0 && typeof process !== 'undefined') process.exit(1);
}

runTests().catch(console.error);
