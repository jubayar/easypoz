import type { PrismaClient } from '../generated/prisma/client.js';
import type { EntityName } from './journal-op.schemas.js';

/**
 * Op appliers (task 3.3): apply a validated payload to the shop's data.
 * All appliers are idempotent (upsert by client-generated id / insert with
 * op-id dedupe upstream) and hard-scope every write to shopId.
 */

type Tx = PrismaClient | Parameters<Parameters<PrismaClient['$transaction']>[0]>[0];

const toDate = (iso: string): Date => new Date(iso);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Payload = any;

export const appliers: {
  [E in EntityName]: (tx: Tx, shopId: string, payload: Payload) => Promise<void>;
} = {
  async product(tx, shopId, p) {
    await tx.product.upsert({
      where: { id: p.id },
      create: {
        id: p.id,
        shopId,
        categoryId: p.categoryId ?? null,
        name: p.name,
        barcode: p.barcode ?? null,
        sku: p.sku ?? null,
        costCents: BigInt(p.costCents ?? 0),
        priceCents: BigInt(p.priceCents),
        taxRateBps: p.taxRateBps ?? null,
        active: p.active ?? true,
        lowStockThreshold: p.lowStockThreshold ?? 5,
        emoji: p.emoji ?? null,
        color: p.color ?? null,
        photo: p.photo ?? null,
        location: p.location ?? null,
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
      },
      update: {
        categoryId: p.categoryId ?? null,
        name: p.name,
        barcode: p.barcode ?? null,
        sku: p.sku ?? null,
        costCents: BigInt(p.costCents ?? 0),
        priceCents: BigInt(p.priceCents),
        taxRateBps: p.taxRateBps ?? null,
        active: p.active ?? true,
        lowStockThreshold: p.lowStockThreshold ?? 5,
        emoji: p.emoji ?? null,
        color: p.color ?? null,
        photo: p.photo ?? null,
        location: p.location ?? null,
        updatedAt: toDate(p.updatedAt),
      },
    });
  },

  async category(tx, shopId, p) {
    await tx.category.upsert({
      where: { id: p.id },
      create: { id: p.id, shopId, name: p.name, sortOrder: p.sortOrder ?? 0, updatedAt: toDate(p.updatedAt) },
      update: { name: p.name, sortOrder: p.sortOrder ?? 0, updatedAt: toDate(p.updatedAt) },
    });
  },

  async user(tx, shopId, p) {
    await tx.user.upsert({
      where: { id: p.id },
      create: {
        id: p.id,
        shopId,
        name: p.name,
        role: p.role,
        pinSalt: p.pinSalt ?? '',
        pinHash: p.pinHash ?? '',
        active: p.active ?? true,
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
      },
      update: {
        name: p.name,
        role: p.role,
        pinSalt: p.pinSalt ?? '',
        pinHash: p.pinHash ?? '',
        active: p.active ?? true,
        updatedAt: toDate(p.updatedAt),
      },
    });
  },

  async sale(tx, shopId, p) {
    // Idempotent insert: if the sale id already exists (replayed op), skip.
    const existing = await tx.sale.findUnique({ where: { id: p.id } });
    if (existing) return;
    await tx.sale.create({
      data: {
        id: p.id,
        shopId,
        localNo: p.localNo,
        userId: p.userId ?? null,
        at: toDate(p.at),
        totalCents: BigInt(p.totalCents),
        paymentTypeId: p.paymentTypeId ?? null,
        tenderedCents: p.tenderedCents != null ? BigInt(p.tenderedCents) : null,
        changeCents: p.changeCents != null ? BigInt(p.changeCents) : null,
        voided: p.voided ?? false,
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
        lines: {
          create: p.lines.map((line: Payload) => ({
            id: line.id,
            shopId,
            productId: line.productId ?? null,
            customName: line.customName ?? null,
            qty: line.qty,
            unitCents: BigInt(line.unitCents),
            lineCents: BigInt(line.lineCents),
            voidReason: line.voidReason ?? null,
            createdAt: toDate(line.createdAt),
            updatedAt: toDate(line.updatedAt),
          })),
        },
      },
    });
  },

  async stock_delta(tx, shopId, p) {
    await tx.stockDelta.upsert({
      where: { id: p.id },
      create: {
        id: p.id,
        shopId,
        productId: p.productId,
        delta: p.delta,
        reason: p.reason,
        note: p.note ?? null,
        refId: p.refId ?? null,
        userId: p.userId ?? null,
        at: toDate(p.at),
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
      },
      update: {}, // ledger rows are immutable; replay is a no-op
    });
  },

  async cash_entry(tx, shopId, p) {
    await tx.cashEntry.upsert({
      where: { id: p.id },
      create: {
        id: p.id,
        shopId,
        direction: p.direction,
        amountCents: BigInt(p.amountCents),
        reason: p.reason,
        userId: p.userId ?? null,
        at: toDate(p.at),
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
      },
      update: {}, // immutable by spec
    });
  },

  async drawer_count(tx, shopId, p) {
    await tx.drawerCount.upsert({
      where: { id: p.id },
      create: {
        id: p.id,
        shopId,
        at: toDate(p.at),
        expectedCents: BigInt(p.expectedCents),
        countedCents: BigInt(p.countedCents),
        varianceCents: BigInt(p.varianceCents),
        userId: p.userId ?? null,
        createdAt: toDate(p.createdAt),
        updatedAt: toDate(p.updatedAt),
      },
      update: {},
    });
  },

  async payment_type(tx, shopId, p) {
    await tx.paymentType.upsert({
      where: { id: p.id },
      create: { id: p.id, shopId, name: p.name, kind: p.kind, active: p.active ?? true, sort: p.sort ?? 0, updatedAt: toDate(p.updatedAt) },
      update: { name: p.name, kind: p.kind, active: p.active ?? true, sort: p.sort ?? 0, updatedAt: toDate(p.updatedAt) },
    });
  },

  async tenant_config(tx, shopId, p) {
    await tx.tenantConfig.upsert({
      where: { shopId },
      create: {
        shopId,
        name: p.name ?? '',
        address: p.address ?? '',
        phone: p.phone ?? '',
        logo: p.logo ?? null,
        currency: p.currency ?? 'MYR',
        language: p.language ?? 'en',
        updatedAt: toDate(p.updatedAt),
      },
      update: {
        name: p.name ?? '',
        address: p.address ?? '',
        phone: p.phone ?? '',
        logo: p.logo ?? null,
        currency: p.currency ?? 'MYR',
        language: p.language ?? 'en',
        updatedAt: toDate(p.updatedAt),
      },
    });
  },

  async setting(tx, shopId, p) {
    await tx.shopSetting.upsert({
      where: { shopId_key: { shopId, key: p.key } },
      create: { shopId, key: p.key, value: p.value, updatedAt: toDate(p.updatedAt) },
      update: { value: p.value, updatedAt: toDate(p.updatedAt) },
    });
  },
};

/** VOID ops mark an existing sale voided (no payload beyond the sale id). */
export async function applySaleVoid(tx: Tx, shopId: string, saleId: string, at: string) {
  await tx.sale.updateMany({
    where: { id: saleId, shopId },
    data: { voided: true, updatedAt: toDate(at) },
  });
}
