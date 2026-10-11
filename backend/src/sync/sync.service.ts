import { ForbiddenException, Injectable } from '@nestjs/common';
import type { Prisma } from '../generated/prisma/client.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { DeviceService } from '../device/device.service.js';
import { appliers, applySaleVoid } from './appliers.js';
import { validateOp, type JournalOpInput } from './journal-op.schemas.js';

const PULL_PAGE_LIMIT = 200;

export type OpAck =
  | { id: string; status: 'applied' }
  | { id: string; status: 'duplicate' }
  | { id: string; status: 'rejected'; errors: { field: string; message: string }[] };

@Injectable()
export class SyncService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly devices: DeviceService,
  ) {}

  /**
   * POST /sync/push body handler: validates each op, applies it in its own
   * transaction, mirrors it into change_log, and acks per op. Op id is the
   * idempotency key — a replayed op acks 'duplicate' without re-applying.
   */
  async push(shopId: string, deviceId: string, ops: JournalOpInput[]): Promise<{ acks: OpAck[] }> {
    if (!(await this.devices.canSync(shopId, deviceId))) {
      throw new ForbiddenException('device is not allowed to sync for this shop');
    }
    const db = this.prisma.require();

    const acks: OpAck[] = [];
    for (const op of ops) {
      const alreadyApplied = await db.changeLog.findUnique({ where: { journalOpId: op.id } });
      if (alreadyApplied) {
        acks.push({ id: op.id, status: 'duplicate' });
        continue;
      }

      const validation = validateOp(op);
      if (!validation.ok) {
        acks.push({ id: op.id, status: 'rejected', errors: validation.errors });
        continue;
      }

      try {
        await db.$transaction(async (tx) => {
          if (op.entity === 'sale' && op.opType === 'VOID') {
            const payload = validation.payload as { id: string; updatedAt: string };
            await applySaleVoid(tx, shopId, payload.id, payload.updatedAt);
          } else {
            await appliers[op.entity](tx, shopId, validation.payload);
          }
          await tx.changeLog.create({
            data: {
              shopId,
              journalOpId: op.id,
              deviceId,
              entity: op.entity,
              entityId: op.entity === 'setting' ? (validation.payload as { key: string }).key : (validation.payload as { id: string }).id,
              opType: op.opType,
              payload: (validation.payload ?? {}) as Prisma.InputJsonValue,
              actorUserId: op.actorUserId ?? null,
            },
          });
        });
        acks.push({ id: op.id, status: 'applied' });
      } catch (error) {
        acks.push({
          id: op.id,
          status: 'rejected',
          errors: [{ field: '(op)', message: error instanceof Error ? error.message : 'apply failed' }],
        });
      }
    }
    return { acks };
  }

  /**
   * GET /sync/pull?since=: ops from other sources, ordered by the monotonic
   * change_log id. `since=0` with a fresh device cursor = full restore.
   */
  async pull(shopId: string, since: number) {
    const db = this.prisma.require();

    const rows = await db.changeLog.findMany({
      where: { shopId, id: { gt: since } },
      orderBy: { id: 'asc' },
      take: PULL_PAGE_LIMIT + 1,
    });

    const hasMore = rows.length > PULL_PAGE_LIMIT;
    const page = hasMore ? rows.slice(0, PULL_PAGE_LIMIT) : rows;
    const cursor = page.length > 0 ? page[page.length - 1].id : since;

    return {
      ops: page.map((row) => ({
        seq: row.id,
        journalOpId: row.journalOpId,
        entity: row.entity,
        entityId: row.entityId,
        opType: row.opType,
        payload: row.payload,
        actorUserId: row.actorUserId,
        createdAt: row.createdAt,
      })),
      cursor: cursor.toString(),
      hasMore,
    };
  }
}
