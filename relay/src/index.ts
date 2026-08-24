import { Hono } from 'hono';

type Bindings = {
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_CHAT_ID: string;
  API_SECRET_KEY?: string;
};

interface StartTripPayload {
  tripId: string;
  captainName?: string;
  latitude: number;
  longitude: number;
  batteryPct: number;
  timestamp: number;
  statusMessageId?: number;
}

interface LocationUpdatePayload {
  tripId: string;
  captainName?: string;
  latitude: number;
  longitude: number;
  speedKnots: number;
  headingDegrees: number;
  headingCardinal: string;
  batteryPct: number;
  timestamp: number;
  accuracyMeters?: number;
  distanceFromHomeNm?: number;
  statusMessageId?: number;
}

interface AlertPayload {
  tripId: string;
  captainName?: string;
  alertType: 'SOS' | 'LOW_BATTERY' | 'GEOFENCE_EXIT' | 'DANGER_ZONE_ENTRY' | 'NO_MOVEMENT' | 'GPS_LOST';
  message?: string;
  latitude: number;
  longitude: number;
  batteryPct: number;
  timestamp: number;
}

interface BatchSyncPayload {
  tripId: string;
  captainName?: string;
  syncTimestamp: number;
  queuedCount: number;
  statusMessageId?: number;
  locations: Array<{
    latitude: number;
    longitude: number;
    speedKnots: number;
    batteryPct: number;
    timestamp: number;
  }>;
  alerts: Array<{
    alertType: string;
    message?: string;
    timestamp: number;
    latitude: number;
    longitude: number;
  }>;
}

interface TripReportPayload {
  tripId: string;
  captainName?: string;
  startTime: number;
  endTime: number;
  totalDistanceNm: number;
  maxSpeedKnots: number;
  avgSpeedKnots: number;
  totalBreadcrumbs: number;
  finalLatitude: number;
  finalLongitude: number;
  statusMessageId?: number;
}

interface TelegramApiResponse {
  ok: boolean;
  result?: { message_id: number };
  description?: string;
}

interface InlineKeyboardMarkup {
  inline_keyboard: Array<Array<{ text: string; url: string }>>;
}

const app = new Hono<{ Bindings: Bindings }>();

app.use('/api/*', async (c, next) => {
  const secret = c.env.API_SECRET_KEY;
  if (secret) {
    const authHeader = c.req.header('Authorization');
    if (!authHeader || authHeader !== `Bearer ${secret}`) {
      return c.json({ error: 'Unauthorized: Invalid API key' }, 401);
    }
  }
  await next();
});

function formatTimestamp(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: 'Indian/Maldives',
  });
}

function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function captainName(value?: string): string {
  return escapeHtml(value || "Father's Boat");
}

function googleMapsLink(latitude: number, longitude: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${latitude.toFixed(5)},${longitude.toFixed(5)}`;
}

function directionsLink(latitude: number, longitude: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${latitude.toFixed(5)},${longitude.toFixed(5)}`;
}

function sharePositionLink(latitude: number, longitude: number): string {
  const mapUrl = encodeURIComponent(googleMapsLink(latitude, longitude));
  const text = encodeURIComponent('Current boat position');
  return `https://t.me/share/url?url=${mapUrl}&text=${text}`;
}

export function locationKeyboard(latitude: number, longitude: number): InlineKeyboardMarkup {
  return {
    inline_keyboard: [[
      { text: '🗺 Open map', url: googleMapsLink(latitude, longitude) },
      { text: '🧭 Directions', url: directionsLink(latitude, longitude) },
    ], [
      { text: '📤 Share position', url: sharePositionLink(latitude, longitude) },
    ]],
  };
}

async function telegramCall(
  botToken: string,
  method: 'sendMessage' | 'editMessageText',
  payload: Record<string, unknown>,
): Promise<TelegramApiResponse> {
  const response = await fetch(`https://api.telegram.org/bot${botToken}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const result = await response.json() as TelegramApiResponse;
  if (!response.ok || !result.ok) {
    throw new Error(result.description || `Telegram ${method} failed with HTTP ${response.status}`);
  }
  return result;
}

async function sendTelegramMessage(
  botToken: string,
  chatId: string,
  text: string,
  replyMarkup: InlineKeyboardMarkup,
): Promise<number> {
  const response = await telegramCall(botToken, 'sendMessage', {
    chat_id: chatId,
    text,
    parse_mode: 'HTML',
    link_preview_options: { is_disabled: true },
    reply_markup: replyMarkup,
  });
  const messageId = response.result?.message_id;
  if (!messageId) throw new Error('Telegram did not return a message ID');
  return messageId;
}

async function upsertTelegramStatus(
  botToken: string,
  chatId: string,
  existingMessageId: number | undefined,
  text: string,
  replyMarkup: InlineKeyboardMarkup,
): Promise<number> {
  if (existingMessageId) {
    try {
      await telegramCall(botToken, 'editMessageText', {
        chat_id: chatId,
        message_id: existingMessageId,
        text,
        parse_mode: 'HTML',
        link_preview_options: { is_disabled: true },
        reply_markup: replyMarkup,
      });
      return existingMessageId;
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      if (detail.toLowerCase().includes('message is not modified')) return existingMessageId;
      const missingMessage = detail.toLowerCase().includes('message to edit not found') ||
        detail.toLowerCase().includes("message can't be edited") ||
        detail.toLowerCase().includes('message can not be edited');
      if (!missingMessage) throw error;
    }
  }
  return sendTelegramMessage(botToken, chatId, text, replyMarkup);
}

app.get('/health', (c) => c.json({ status: 'ok', service: 'captain-avi-relay' }));

app.post('/api/trip/start', async (c) => {
  const body = await c.req.json<StartTripPayload>();
  const message = [
    '🟢 <b>TRIP ACTIVE</b>',
    `👤 <b>Captain:</b> ${captainName(body.captainName)}`,
    `📍 <b>Departure:</b> <code>${body.latitude.toFixed(5)}, ${body.longitude.toFixed(5)}</code>`,
    `🔋 <b>Battery:</b> ${body.batteryPct}%`,
    `🕒 <b>Started:</b> ${formatTimestamp(body.timestamp)}`,
    'ℹ️ This card will refresh in place. Safety alerts remain separate.',
  ].join('\n');
  const messageId = await upsertTelegramStatus(
    c.env.TELEGRAM_BOT_TOKEN,
    c.env.TELEGRAM_CHAT_ID,
    body.statusMessageId,
    message,
    locationKeyboard(body.latitude, body.longitude),
  );
  return c.json({ success: true, messageId });
});

app.post('/api/trip/update', async (c) => {
  const body = await c.req.json<LocationUpdatePayload>();
  const lines = [
    '🟢 <b>LIVE TRIP STATUS</b>',
    `👤 <b>${captainName(body.captainName)}</b>`,
    `📍 <b>Position:</b> <code>${body.latitude.toFixed(5)}, ${body.longitude.toFixed(5)}</code>`,
    `⚡ <b>Speed:</b> ${body.speedKnots.toFixed(1)} kt`,
    `🧭 <b>Heading:</b> ${escapeHtml(body.headingCardinal)} (${Math.round(body.headingDegrees)}°)`,
    `🔋 <b>Battery:</b> ${body.batteryPct}%`,
  ];
  if (body.distanceFromHomeNm !== undefined) {
    lines.push(`🏠 <b>From home:</b> ${body.distanceFromHomeNm.toFixed(1)} NM`);
  }
  lines.push(`🕒 <b>Updated:</b> ${formatTimestamp(body.timestamp)}`);
  const messageId = await upsertTelegramStatus(
    c.env.TELEGRAM_BOT_TOKEN,
    c.env.TELEGRAM_CHAT_ID,
    body.statusMessageId,
    lines.join('\n'),
    locationKeyboard(body.latitude, body.longitude),
  );
  return c.json({ success: true, messageId });
});

app.post('/api/trip/alert', async (c) => {
  const body = await c.req.json<AlertPayload>();
  const icon = body.alertType === 'SOS' ? '🚨🆘🚨' : '⚠️';
  const message = [
    `${icon} <b>MARINE ALERT: ${escapeHtml(body.alertType)}</b>`,
    `👤 <b>Captain:</b> ${captainName(body.captainName)}`,
    `ℹ️ <b>Detail:</b> ${escapeHtml(body.message || 'Emergency beacon')}`,
    `📍 <b>Position:</b> <code>${body.latitude.toFixed(5)}, ${body.longitude.toFixed(5)}</code>`,
    `🔋 <b>Battery:</b> ${body.batteryPct}%`,
    `🕒 <b>Time:</b> ${formatTimestamp(body.timestamp)}`,
  ].join('\n');
  await sendTelegramMessage(
    c.env.TELEGRAM_BOT_TOKEN,
    c.env.TELEGRAM_CHAT_ID,
    message,
    locationKeyboard(body.latitude, body.longitude),
  );
  return c.json({ success: true, message: 'Alert dispatched' });
});

app.post('/api/trip/batch-sync', async (c) => {
  const body = await c.req.json<BatchSyncPayload>();
  const latest = body.locations.at(-1);
  let messageId = body.statusMessageId;
  if (latest) {
    const lines = [
      '🟢 <b>LIVE TRIP STATUS</b>',
      `👤 <b>${captainName(body.captainName)}</b>`,
      `📍 <b>Position:</b> <code>${latest.latitude.toFixed(5)}, ${latest.longitude.toFixed(5)}</code>`,
      `⚡ <b>Speed:</b> ${latest.speedKnots.toFixed(1)} kt`,
      `🔋 <b>Battery:</b> ${latest.batteryPct}%`,
      body.queuedCount > 1 ? `📶 <b>Synced:</b> ${body.queuedCount} stored positions` : '',
      `🕒 <b>Updated:</b> ${formatTimestamp(latest.timestamp)}`,
    ].filter(Boolean);
    messageId = await upsertTelegramStatus(
      c.env.TELEGRAM_BOT_TOKEN,
      c.env.TELEGRAM_CHAT_ID,
      body.statusMessageId,
      lines.join('\n'),
      locationKeyboard(latest.latitude, latest.longitude),
    );
  }
  if (body.alerts.length > 0) {
    const latestAlert = [...body.alerts].sort((a, b) => b.timestamp - a.timestamp)[0];
    const alertMessage = [
      '⚠️ <b>OFFLINE SAFETY EVENTS SYNCED</b>',
      `👤 <b>${captainName(body.captainName)}</b>`,
      `📋 <b>Events:</b> ${body.alerts.length}`,
      `🚨 <b>Latest:</b> ${escapeHtml(latestAlert.alertType)}`,
      `ℹ️ ${escapeHtml(latestAlert.message || 'Safety event')}`,
      `🕒 <b>Recorded:</b> ${formatTimestamp(latestAlert.timestamp)}`,
    ].join('\n');
    await sendTelegramMessage(
      c.env.TELEGRAM_BOT_TOKEN,
      c.env.TELEGRAM_CHAT_ID,
      alertMessage,
      locationKeyboard(latestAlert.latitude, latestAlert.longitude),
    );
  }
  return c.json({ success: true, messageId, queuedCount: body.locations.length, alertsSynced: body.alerts.length });
});

app.post('/api/trip/end', async (c) => {
  const body = await c.req.json<TripReportPayload>();
  const durationMinutes = Math.max(1, Math.round((body.endTime - body.startTime) / 60_000));
  const keyboard = locationKeyboard(body.finalLatitude, body.finalLongitude);
  const completedStatus = [
    '✅ <b>TRIP COMPLETED</b>',
    `👤 <b>Captain:</b> ${captainName(body.captainName)}`,
    `⏱️ <b>Duration:</b> ${durationMinutes} min`,
    `📏 <b>Travelled:</b> ${body.totalDistanceNm.toFixed(2)} NM`,
    `🚀 <b>Speed:</b> max ${body.maxSpeedKnots.toFixed(1)} kt · avg ${body.avgSpeedKnots.toFixed(1)} kt`,
    `📍 <b>Final position:</b> <code>${body.finalLatitude.toFixed(5)}, ${body.finalLongitude.toFixed(5)}</code>`,
    `🕒 <b>Finished:</b> ${formatTimestamp(body.endTime)}`,
  ].join('\n');
  const messageId = await upsertTelegramStatus(
    c.env.TELEGRAM_BOT_TOKEN,
    c.env.TELEGRAM_CHAT_ID,
    body.statusMessageId,
    completedStatus,
    keyboard,
  );
  await sendTelegramMessage(
    c.env.TELEGRAM_BOT_TOKEN,
    c.env.TELEGRAM_CHAT_ID,
    [
      '🏁 <b>ARRIVED SAFELY</b>',
      `${captainName(body.captainName)} completed the trip.`,
      `📏 ${body.totalDistanceNm.toFixed(2)} NM · ⏱️ ${durationMinutes} min`,
    ].join('\n'),
    keyboard,
  );
  return c.json({ success: true, messageId, message: 'Trip completed' });
});

export default app;
