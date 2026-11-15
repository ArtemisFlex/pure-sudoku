// PureGames room codes: the only server piece of online play.
//
// Two devices swap their connection details here using a 4-letter code, then talk to each other
// directly. If a direct link can't be made on some networks, moves (a few bytes each) are passed
// through here instead. Everything expires on its own: rooms after 15 minutes unanswered, games
// after 2 hours. Nothing about the players is stored.
//
// Storage: Upstash Redis over its REST API (KV_REST_API_URL / KV_REST_API_TOKEN, as Vercel's
// Upstash integration sets them, or UPSTASH_REDIS_REST_URL / UPSTASH_REDIS_REST_TOKEN). Without
// them it keeps rooms in memory, which is only good for local testing.

const URL_ = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

const ROOM_TTL = 15 * 60;
const GAME_TTL = 2 * 60 * 60;
const LETTERS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // no 0/O, 1/I/L
const KINDS = /^[A-Z_]{2,24}$/;
const CODE = /^[A-Z2-9]{4}$/;
const MAX_SDP = 16000;
const MAX_MSG = 200000;
const MAX_QUEUE = 2000;

// ------------------------------------------------------------------ storage

const mem = globalThis.__pgRooms || (globalThis.__pgRooms = new Map());
function memGet(k) { const e = mem.get(k); if (!e) return null; if (e.until < Date.now()) { mem.delete(k); return null; } return e.v; }

async function redis(...cmd) {
  if (!URL_ || !TOKEN) return memRedis(cmd);
  const r = await fetch(URL_, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd),
  });
  if (!r.ok) throw new Error(`store ${r.status}`);
  return (await r.json()).result;
}

function memRedis([op, key, ...a]) {
  const now = Date.now();
  switch (op) {
    case 'SET': {
      const nx = a.includes('NX'); const ex = Number(a[a.indexOf('EX') + 1]);
      if (nx && memGet(key) != null) return null;
      mem.set(key, { v: a[0], until: now + ex * 1000 }); return 'OK';
    }
    case 'GET': return memGet(key);
    case 'RPUSH': { const l = memGet(key) || []; l.push(a[0]); const e = mem.get(key); mem.set(key, { v: l, until: e ? e.until : now + GAME_TTL * 1000 }); return l.length; }
    case 'EXPIRE': { const e = mem.get(key); if (e) e.until = now + Number(a[0]) * 1000; return 1; }
    case 'LRANGE': { const l = memGet(key) || []; return l.slice(Number(a[0])); }
    default: throw new Error('op');
  }
}

// ------------------------------------------------------------------ handler

function newCode() {
  let s = '';
  for (let i = 0; i < 4; i++) s += LETTERS[Math.floor(Math.random() * LETTERS.length)];
  return s;
}

async function readBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  if (typeof req.body === 'string') return JSON.parse(req.body || '{}');
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const text = Buffer.concat(chunks).toString('utf8');
  return text ? JSON.parse(text) : {};
}

function send(res, status, obj) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.end(JSON.stringify(obj));
}

async function handler(req, res) {
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.statusCode = 204; return res.end();
  }
  try {
    const q = new URL(req.url, 'http://x').searchParams;
    const b = req.method === 'POST' ? await readBody(req) : {};
    const op = b.op || q.get('op');
    const code = String(b.code || q.get('code') || '').toUpperCase();
    if (op !== 'create' && !CODE.test(code)) return send(res, 400, { error: 'bad code' });

    switch (op) {
      case 'create': {
        const kind = String(b.kind || ''); const offer = String(b.offer || '');
        if (!KINDS.test(kind) || !offer || offer.length > MAX_SDP) return send(res, 400, { error: 'bad room' });
        for (let tries = 0; tries < 8; tries++) {
          const c = newCode();
          if (await redis('SET', `room:${c}`, JSON.stringify({ kind, offer }), 'NX', 'EX', ROOM_TTL) === 'OK') return send(res, 200, { code: c });
        }
        return send(res, 503, { error: 'busy' });
      }
      case 'offer': {
        const v = await redis('GET', `room:${code}`);
        return v ? send(res, 200, JSON.parse(v)) : send(res, 404, { error: 'no such room' });
      }
      case 'answer': {
        if (req.method === 'POST') {
          const answer = String(b.answer || '');
          if (!answer || answer.length > MAX_SDP) return send(res, 400, { error: 'bad answer' });
          if (!(await redis('GET', `room:${code}`))) return send(res, 404, { error: 'no such room' });
          await redis('SET', `room:${code}:ans`, answer, 'EX', ROOM_TTL);
          return send(res, 200, { ok: true });
        }
        return send(res, 200, { answer: await redis('GET', `room:${code}:ans`) });
      }
      case 'send': {
        const seat = Number(b.seat); const msg = String(b.msg || '');
        if (![1, 2].includes(seat) || !msg || msg.length > MAX_MSG) return send(res, 400, { error: 'bad message' });
        const key = `room:${code}:to${3 - seat}`;
        const n = await redis('RPUSH', key, msg);
        if (n > MAX_QUEUE) return send(res, 429, { error: 'too many messages' });
        await redis('EXPIRE', key, GAME_TTL);
        return send(res, 200, { ok: true });
      }
      case 'recv': {
        const seat = Number(q.get('seat')); const from = Math.max(0, Number(q.get('from')) || 0);
        if (![1, 2].includes(seat)) return send(res, 400, { error: 'bad seat' });
        const msgs = (await redis('LRANGE', `room:${code}:to${seat}`, from, -1)) || [];
        return send(res, 200, { msgs });
      }
      default: return send(res, 400, { error: 'bad op' });
    }
  } catch (e) {
    return send(res, 500, { error: 'server' });
  }
}

module.exports = handler;
