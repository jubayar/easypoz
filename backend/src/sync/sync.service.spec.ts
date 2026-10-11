import { ForbiddenException } from '@nestjs/common';
import { describe, expect, it } from 'vitest';
import { SyncService } from './sync.service.js';
import { DeviceService } from '../device/device.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import type { JournalOpInput } from './journal-op.schemas.js';

function productOp(overrides: Partial<JournalOpInput> = {}): JournalOpInput {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    seq: 1,
    entity: 'product',
    opType: 'UPSERT',
    actorUserId: null,
    payload: {
      id: '22222222-2222-4222-8222-222222222222',
      name: 'Milo 1kg',
      priceCents: 1200,
      costCents: 950,
      createdAt: '2026-10-07T10:00:00.000Z',
      updatedAt: '2026-10-07T10:00:00.000Z',
    },
    createdAt: '2026-10-07T10:00:00.000Z',
    ...overrides,
  };
}

function syncFixtures(canSync = true) {
  const applied: { entity: string; payload: unknown; shopId: string }[] = [];
  const changeLogRows: { journalOpId: string; shopId: string; entity: string; entityId: string; id: number }[] = [];

  const db = {
    changeLog: {
      findUnique: async ({ where }: { where: { journalOpId: string } }) =>
        changeLogRows.find((r) => r.journalOpId === where.journalOpId) ?? null,
      findMany: async ({ where, take }: { where: { shopId: string; id: { gt: number } }; orderBy: unknown; take: number }) =>
        changeLogRows
          .filter((r) => r.shopId === where.shopId && r.id > where.id.gt)
          .sort((a, b) => a.id - b.id)
          .slice(0, take),
      create: async ({ data }: { data: { journalOpId: string; shopId: string; entity: string; entityId: string } }) => {
        changeLogRows.push({ id: changeLogRows.length + 1, ...data });
        return changeLogRows[changeLogRows.length - 1];
      },
    },
    product: {
      upsert: async ({ where, create }: { where: { id: string }; create: Record<string, unknown> }) => {
        applied.push({ entity: 'product', payload: create, shopId: String(create.shopId) });
        return { id: where.id, ...create };
      },
    },
    $transaction: async (fn: (tx: unknown) => Promise<unknown>) => fn(db),
  };

  const devices = { canSync: async () => canSync } as unknown as DeviceService;
  const service = new SyncService({ require: () => db } as unknown as PrismaService, devices);
  return { service, applied, changeLogRows, db };
}

describe('SyncService.push', () => {
  it('applies a valid op once and mirrors it into change_log', async () => {
    const f = syncFixtures();
    const { acks } = await f.service.push('shop-1', 'dev-1', [productOp()]);
    expect(acks).toEqual([{ id: productOp().id, status: 'applied' }]);
    expect(f.applied).toHaveLength(1);
    expect(f.applied[0].shopId).toBe('shop-1'); // tenant scoping
    expect(f.changeLogRows).toHaveLength(1);
  });

  it('acks duplicate on replay without re-applying', async () => {
    const f = syncFixtures();
    const op = productOp();
    await f.service.push('shop-1', 'dev-1', [op]);
    const second = await f.service.push('shop-1', 'dev-1', [op]);
    expect(second.acks[0].status).toBe('duplicate');
    expect(f.applied).toHaveLength(1);
  });

  it('rejects a malformed op with field-level errors', async () => {
    const f = syncFixtures();
    const op = productOp({
      payload: { id: '22222222-2222-4222-8222-222222222222', name: '', priceCents: -5 },
    });
    const { acks } = await f.service.push('shop-1', 'dev-1', [op]);
    if (acks[0].status !== 'rejected') throw new Error('expected rejected');
    const fields = acks[0].errors.map((e) => e.field);
    expect(fields).toContain('payload.name');
    expect(fields).toContain('payload.priceCents');
  });

  it('rejects an op with a disallowed opType for the entity', async () => {
    const f = syncFixtures();
    const { acks } = await f.service.push('shop-1', 'dev-1', [productOp({ opType: 'DELETE' })]);
    if (acks[0].status !== 'rejected') throw new Error('expected rejected');
    expect(acks[0].errors[0].field).toBe('opType');
  });

  it('forbids pushes from a device outside the grace window', async () => {
    const f = syncFixtures(false);
    await expect(f.service.push('shop-1', 'dev-1', [productOp()])).rejects.toThrowError(
      ForbiddenException,
    );
  });

  it('mixes acks in one batch: valid, invalid, valid', async () => {
    const f = syncFixtures();
    const good1 = productOp({ id: '11111111-1111-4111-8111-111111111111' });
    const bad = productOp({
      id: '11111111-1111-4111-8111-111111111112',
      payload: { ...productOp().payload, name: '' },
    });
    const good2 = productOp({ id: '11111111-1111-4111-8111-111111111113' });
    const { acks } = await f.service.push('shop-1', 'dev-1', [good1, bad, good2]);
    expect(acks.map((a) => a.status)).toEqual(['applied', 'rejected', 'applied']);
  });
});

describe('SyncService.pull', () => {
  it('returns ops after the cursor in order with paging metadata', async () => {
    const f = syncFixtures();
    for (let i = 0; i < 3; i++) {
      await f.service.push('shop-1', 'dev-1', [productOp({ id: `11111111-1111-4111-8111-11111111111${i}` })]);
    }
    // also an op for another shop — must never leak
    f.changeLogRows.push({
      id: 99,
      journalOpId: '99999999-9999-4999-8999-999999999999',
      shopId: 'shop-other',
      entity: 'product',
      entityId: 'x',
    });

    const firstPage = await f.service.pull('shop-1', 0);
    expect(firstPage.ops).toHaveLength(3);
    expect(firstPage.hasMore).toBe(false);
    expect(firstPage.cursor).toBe('3');

    const secondPage = await f.service.pull('shop-1', Number(firstPage.cursor));
    expect(secondPage.ops).toHaveLength(0);
    expect(secondPage.cursor).toBe(firstPage.cursor);
  });
});
