import { ConflictException, NotFoundException } from '@nestjs/common';
import { describe, expect, it } from 'vitest';
import { LicenseService } from './license.service.js';
import { LicenseSigner } from './license.signer.js';
import { PrismaService } from '../prisma/prisma.service.js';

type LicenseRow = {
  id: string;
  shopId: string;
  status: 'TRIAL' | 'ACTIVE' | 'EXPIRED';
  trialDays: number;
  trialStartedAt: Date | null;
  activatedAt: Date | null;
  expiresAt: Date | null;
};

type KeyRow = {
  key: string;
  planDays: number;
  boundToShopId: string | null;
  boundAt: Date | null;
};

const DAY = 24 * 60 * 60 * 1000;

function fixtures() {
  const licenses: LicenseRow[] = [];
  const keys: KeyRow[] = [];
  const db = {
    license: {
      findFirst: async ({ where }: { where: Record<string, unknown> }) =>
        licenses.find((l) => l.shopId === where.shopId && (!where.status || l.status === where.status)) ??
        null,
      create: async ({ data }: { data: Omit<LicenseRow, 'id'> }) => {
        const row = { id: `lic-${licenses.length + 1}`, ...data };
        licenses.push(row);
        return row;
      },
      update: async ({ where, data }: { where: { id: string }; data: Partial<LicenseRow> }) => {
        const row = licenses.find((l) => l.id === where.id)!;
        Object.assign(row, data);
        return row;
      },
    },
    licenseKey: {
      findUnique: async ({ where }: { where: { key: string } }) =>
        keys.find((k) => k.key === where.key) ?? null,
      update: async ({ where, data }: { where: { key: string }; data: Partial<KeyRow> }) => {
        const row = keys.find((k) => k.key === where.key)!;
        Object.assign(row, data);
        return row;
      },
    },
    $transaction: async (fn: (tx: typeof db) => Promise<unknown>) => fn(db),
  };
  const signer = new LicenseSigner('test-signing-key');
  const service = new LicenseService({ require: () => db } as unknown as PrismaService, signer);
  return { licenses, keys, db, signer, service };
}

describe('LicenseService.status', () => {
  it('counts trial days left from trialStartedAt', async () => {
    const f = fixtures();
    f.licenses.push({
      id: 'lic-1',
      shopId: 'shop-1',
      status: 'TRIAL',
      trialDays: 14,
      trialStartedAt: new Date(Date.now() - 12 * DAY),
      activatedAt: null,
      expiresAt: null,
    });
    const view = await f.service.status('shop-1');
    expect(view.status).toBe('TRIAL');
    expect(view.trialDaysLeft).toBe(2);
  });

  it('reports ACTIVE with expiry when an active license exists', async () => {
    const f = fixtures();
    f.licenses.push({
      id: 'lic-1',
      shopId: 'shop-1',
      status: 'ACTIVE',
      trialDays: 14,
      trialStartedAt: new Date(Date.now() - 40 * DAY),
      activatedAt: new Date(),
      expiresAt: new Date(Date.now() + 200 * DAY),
    });
    const view = await f.service.status('shop-1');
    expect(view.status).toBe('ACTIVE');
    expect(view.expiresAt).toBeTruthy();
  });

  it('reports EXPIRED with no license rows', async () => {
    const f = fixtures();
    const view = await f.service.status('shop-1');
    expect(view.status).toBe('EXPIRED');
    expect(view.expiresAt).toBeNull();
  });
});

describe('LicenseService.activate', () => {
  it('binds a free key to the shop and activates', async () => {
    const f = fixtures();
    f.licenses.push({
      id: 'lic-1',
      shopId: 'shop-1',
      status: 'TRIAL',
      trialDays: 14,
      trialStartedAt: new Date(),
      activatedAt: null,
      expiresAt: null,
    });
    f.keys.push({ key: 'EASY-AAAAA-BBBBB-CCCCC', planDays: 365, boundToShopId: null, boundAt: null });

    const view = await f.service.activate('shop-1', 'easy-aaaaa-bbbbb-ccccc');
    expect(view.status).toBe('ACTIVE');
    expect(f.keys[0].boundToShopId).toBe('shop-1');
    expect(f.licenses[0].status).toBe('ACTIVE');
  });

  it('rejects an unknown key', async () => {
    const f = fixtures();
    await expect(f.service.activate('shop-1', 'EASY-ZZZZZ-ZZZZZ-ZZZZZ')).rejects.toThrowError(
      NotFoundException,
    );
  });

  it('rejects a key bound to another shop', async () => {
    const f = fixtures();
    f.keys.push({
      key: 'EASY-AAAAA-BBBBB-CCCCC',
      planDays: 365,
      boundToShopId: 'shop-other',
      boundAt: new Date(),
    });
    await expect(f.service.activate('shop-1', 'EASY-AAAAA-BBBBB-CCCCC')).rejects.toThrowError(
      ConflictException,
    );
  });

  it('re-activating the same key is idempotent', async () => {
    const f = fixtures();
    f.licenses.push({
      id: 'lic-1',
      shopId: 'shop-1',
      status: 'ACTIVE',
      trialDays: 14,
      trialStartedAt: new Date(Date.now() - 40 * DAY),
      activatedAt: new Date(),
      expiresAt: new Date(Date.now() + 300 * DAY),
    });
    f.keys.push({
      key: 'EASY-AAAAA-BBBBB-CCCCC',
      planDays: 365,
      boundToShopId: 'shop-1',
      boundAt: new Date(),
    });
    const view = await f.service.activate('shop-1', 'EASY-AAAAA-BBBBB-CCCCC');
    expect(view.status).toBe('ACTIVE');
  });
});

describe('LicenseSigner', () => {
  it('round-trips and detects tampering', () => {
    const signer = new LicenseSigner('secret');
    const token = signer.sign({
      shopId: 'shop-1',
      status: 'TRIAL',
      expiresAt: new Date(Date.now() + 2 * DAY).toISOString(),
      issuedAt: new Date().toISOString(),
    });
    expect(signer.verify(token)?.shopId).toBe('shop-1');

    const [body] = token.split('.');
    const forged = `${body}.forged-signature`;
    expect(signer.verify(forged)).toBeNull();

    // A different signing key rejects the same payload.
    expect(new LicenseSigner('other').verify(token)).toBeNull();
  });
});
