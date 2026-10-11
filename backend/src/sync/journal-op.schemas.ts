import { z } from 'zod';

/**
 * JournalOp wire format (design D4/D5). Every mutation the tablet commits
 * locally appends one op; push batches them; the server applies each
 * idempotently keyed on op id and mirrors the row into change_log.
 *
 * Money is integer sen (cents) everywhere — JSON numbers are exact to 2^53,
 * far above any shop amount. Timestamps travel as ISO strings.
 */

export const isoDate = z.string().datetime({ offset: true });
const cents = z.number().int().nonnegative();
const uuid = z.string().uuid();

const userRole = z.enum(['ADMIN', 'MANAGER', 'CASHIER']);

export const productPayload = z.object({
  id: uuid,
  categoryId: uuid.nullable().optional(),
  name: z.string().trim().min(1).max(200),
  barcode: z.string().trim().max(64).nullable().optional(),
  sku: z.string().trim().max(64).nullable().optional(),
  costCents: cents.default(0),
  priceCents: cents,
  taxRateBps: z.number().int().min(0).max(10_000).nullable().optional(),
  active: z.boolean().default(true),
  lowStockThreshold: z.number().int().min(0).default(5),
  emoji: z.string().max(16).nullable().optional(),
  color: z.string().max(16).nullable().optional(),
  photo: z.string().nullable().optional(),
  location: z.string().max(64).nullable().optional(),
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const categoryPayload = z.object({
  id: uuid,
  name: z.string().trim().min(1).max(80),
  sortOrder: z.number().int().default(0),
  updatedAt: isoDate,
});

export const userPayload = z.object({
  id: uuid,
  name: z.string().trim().min(1).max(120),
  role: userRole,
  pinSalt: z.string().max(128).default(''),
  pinHash: z.string().max(256).default(''),
  active: z.boolean().default(true),
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const saleLinePayload = z.object({
  id: uuid,
  productId: uuid.nullable().optional(),
  customName: z.string().max(200).nullable().optional(),
  qty: z.number().int().positive(),
  unitCents: cents,
  lineCents: cents,
  voidReason: z.string().max(200).nullable().optional(),
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const salePayload = z.object({
  id: uuid,
  localNo: z.number().int().positive(),
  userId: uuid.nullable().optional(),
  at: isoDate,
  totalCents: cents,
  paymentTypeId: uuid.nullable().optional(),
  tenderedCents: cents.nullable().optional(),
  changeCents: cents.nullable().optional(),
  voided: z.boolean().default(false),
  lines: z.array(saleLinePayload).min(1),
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const stockDeltaPayload = z.object({
  id: uuid,
  productId: uuid,
  delta: z.number().int(),
  reason: z.enum(['SALE', 'ADJUST', 'IMPORT']),
  note: z.string().max(300).nullable().optional(),
  refId: z.string().max(64).nullable().optional(),
  userId: uuid.nullable().optional(),
  at: isoDate,
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const cashEntryPayload = z.object({
  id: uuid,
  direction: z.enum(['IN', 'OUT']),
  amountCents: z.number().int().positive(),
  reason: z.string().trim().min(1).max(200),
  userId: uuid.nullable().optional(),
  at: isoDate,
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const drawerCountPayload = z.object({
  id: uuid,
  at: isoDate,
  expectedCents: cents,
  countedCents: cents,
  varianceCents: z.number().int(),
  userId: uuid.nullable().optional(),
  createdAt: isoDate,
  updatedAt: isoDate,
});

export const paymentTypePayload = z.object({
  id: uuid,
  name: z.string().trim().min(1).max(60),
  kind: z.enum(['CASH', 'OTHER']),
  active: z.boolean().default(true),
  sort: z.number().int().default(0),
  updatedAt: isoDate,
});

export const tenantConfigPayload = z.object({
  name: z.string().trim().max(160).default(''),
  address: z.string().max(300).default(''),
  phone: z.string().max(40).default(''),
  logo: z.string().nullable().optional(),
  currency: z.string().length(3).default('MYR'),
  language: z.enum(['en', 'ms']).default('en'),
  updatedAt: isoDate,
});

export const settingPayload = z.object({
  key: z.string().trim().min(1).max(80),
  value: z.string().max(2000),
  updatedAt: isoDate,
});

/** entity → payload schema. opType is UPSERT for snapshot-style rows. */
const entitySchemas = {
  product: productPayload,
  category: categoryPayload,
  user: userPayload,
  sale: salePayload,
  stock_delta: stockDeltaPayload,
  cash_entry: cashEntryPayload,
  drawer_count: drawerCountPayload,
  payment_type: paymentTypePayload,
  tenant_config: tenantConfigPayload,
  setting: settingPayload,
} as const;

export type EntityName = keyof typeof entitySchemas;
export const entityNames = Object.keys(entitySchemas) as EntityName[];

/** Allowed op types per entity — snapshot-style rows are UPSERT, ledgers INSERT. */
const opTypesByEntity: Record<EntityName, string[]> = {
  product: ['UPSERT'],
  category: ['UPSERT'],
  user: ['UPSERT'],
  sale: ['INSERT', 'VOID'],
  stock_delta: ['INSERT'],
  cash_entry: ['INSERT'],
  drawer_count: ['INSERT'],
  payment_type: ['UPSERT'],
  tenant_config: ['UPSERT'],
  setting: ['UPSERT'],
};

export const journalOpSchema = z.object({
  id: uuid,
  seq: z.number().int().nonnegative(),
  entity: z.enum(entityNames),
  opType: z.string().min(1),
  actorUserId: uuid.nullable().optional(),
  payload: z.unknown(),
  createdAt: isoDate,
});

export type JournalOpInput = z.infer<typeof journalOpSchema>;

export const pushBatchSchema = z.object({
  deviceId: uuid,
  ops: z.array(journalOpSchema).min(1).max(200),
});

export type PushBatch = z.infer<typeof pushBatchSchema>;

/** Validates (entity, opType) and the payload together. */
export function validateOp(op: JournalOpInput):
  | { ok: true; payload: unknown }
  | { ok: false; errors: { field: string; message: string }[] } {
  const allowed = opTypesByEntity[op.entity];
  if (!allowed.includes(op.opType)) {
    return {
      ok: false,
      errors: [
        { field: 'opType', message: `must be one of ${allowed.join(' | ')} for ${op.entity}` },
      ],
    };
  }
  const payloadResult = entitySchemas[op.entity].safeParse(op.payload);
  if (!payloadResult.success) {
    return {
      ok: false,
      errors: payloadResult.error.issues.map((issue) => ({
        field: `payload.${issue.path.join('.')}`,
        message: issue.message,
      })),
    };
  }
  return { ok: true, payload: payloadResult.data };
}
