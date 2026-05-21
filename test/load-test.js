import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import crypto from 'k6/crypto';

// ─── Config ───────────────────────────────────────────────────────────────────
const BASE_URL       = 'http://localhost:9090';
const WEBHOOK_SECRET = 'CHANGE_MEdaddybdhchedhdccgvchech';

// ─── Custom metrics ───────────────────────────────────────────────────────────
const errorRate    = new Rate('webhook_errors');
const successCount = new Counter('webhook_success');
const latency      = new Trend('webhook_latency', true);

// ─── Test config ──────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    webhook_load: {
      executor:        'constant-arrival-rate',
      rate:            80,        // 80 req/s — under the 100/60s rate limit
      timeUnit:        '1s',
      duration:        '30s',
      preAllocatedVUs: 50,
      maxVUs:          100,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.05'],  // 5% — accounts for warmup
    webhook_errors:    ['rate<0.05'],
  },
};

// ─── HMAC-SHA256 ──────────────────────────────────────────────────────────────
function sign(payload, secret) {
  const hmac = crypto.createHMAC('sha256', secret);
  hmac.update(payload);
  return hmac.digest('hex');
}

// ─── Main ─────────────────────────────────────────────────────────────────────
export default function () {
  const ts        = Date.now();
  const eventId   = `evt_k6_${__VU}_${__ITER}_${ts}`;
  const paymentId = `pay_k6_${__VU}_${__ITER}_${ts}`;
  const epoch     = Math.floor(ts / 1000);
  const amount    = 50000 + Math.floor(Math.random() * 100000);

  const payload = JSON.stringify({
    id:         eventId,
    event:      'payment.captured',
    created_at: epoch,
    payload: {
      payment: {
        entity: {
          id:                paymentId,
          amount:            amount,
          currency:          'INR',
          status:            'captured',
          method:            'card',
          order_id:          null,
          email:             'test@example.com',
          contact:           '+919999999999',
          error_description: null,
        },
      },
    },
  });

  const signature = sign(payload, WEBHOOK_SECRET);

  const res = http.post(`${BASE_URL}/webhooks/razorpay`, payload, {
    headers: {
      'Content-Type':         'application/json',
      'X-Razorpay-Signature': signature,
    },
  });

  const ok = check(res, {
    'status 200':          (r) => r.status === 200,
    'body has received':   (r) => r.body && r.body.includes('received'),
  });

  errorRate.add(!ok);
  if (ok) successCount.add(1);
  latency.add(res.timings.duration);
  // No sleep — arrival rate is controlled by constant-arrival-rate executor
}

// ─── Summary ──────────────────────────────────────────────────────────────────
export function handleSummary(data) {
  const reqs     = data.metrics.http_reqs.values.count;
  const duration = data.metrics.http_req_duration.values;
  const errors   = data.metrics.http_req_failed.values.rate;

  console.log('\n════════════════════════════════════════');
  console.log('           LOAD TEST SUMMARY            ');
  console.log('════════════════════════════════════════');
  console.log(`Total requests : ${reqs}`);
  console.log(`RPS (avg)      : ${(reqs / 30).toFixed(1)}`);
  console.log(`p50 latency    : ${duration['p(50)'].toFixed(1)}ms`);
  console.log(`p95 latency    : ${duration['p(95)'].toFixed(1)}ms`);
  console.log(`p99 latency    : ${duration['p(99)'].toFixed(1)}ms`);
  console.log(`Error rate     : ${(errors * 100).toFixed(2)}%`);
  console.log('════════════════════════════════════════\n');

  return {
    'test/load-test-results.json': JSON.stringify(data, null, 2),
  };
}