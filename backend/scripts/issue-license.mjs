#!/usr/bin/env node
/**
 * Issues license keys after out-of-app payment (bank transfer / toyyibPay).
 * Usage:
 *   node scripts/issue-license.mjs [--days 365] [--count 1]
 * Requires DATABASE_URL (see .env) — inserts unbound license_keys rows and
 * prints the keys to hand to the customer.
 */
import 'dotenv/config';
import crypto from 'node:crypto';
import pg from 'pg';

// Crockford base32: no I, L, O, U — hard to misread over WhatsApp.
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

function group() {
  let out = '';
  for (let i = 0; i < 5; i++) out += ALPHABET[crypto.randomInt(ALPHABET.length)];
  return out;
}

function newKey() {
  return `EASY-${group()}-${group()}-${group()}`;
}

function parseArgs(argv) {
  const args = { days: 365, count: 1 };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--days') args.days = Number(argv[++i]);
    else if (argv[i] === '--count') args.count = Number(argv[++i]);
    else {
      console.error(`unknown argument: ${argv[i]}`);
      process.exit(2);
    }
  }
  if (!Number.isInteger(args.days) || args.days <= 0) {
    console.error('--days must be a positive integer');
    process.exit(2);
  }
  if (!Number.isInteger(args.count) || args.count <= 0 || args.count > 100) {
    console.error('--count must be an integer between 1 and 100');
    process.exit(2);
  }
  return args;
}

const { days, count } = parseArgs(process.argv);

const url = process.env.DATABASE_URL;
if (!url) {
  console.error('DATABASE_URL is not set — configure .env first');
  process.exit(1);
}

const client = new pg.Client({ connectionString: url });
await client.connect();

const issued = [];
try {
  for (let i = 0; i < count; i++) {
    const key = newKey();
    await client.query(
      'INSERT INTO license_keys (id, key, plan_days) VALUES (gen_random_uuid(), $1, $2)',
      [key, days],
    );
    issued.push(key);
  }
} finally {
  await client.end();
}

console.log(`Issued ${issued.length} key(s), ${days} day plan each:`);
for (const key of issued) console.log(`  ${key}`);
