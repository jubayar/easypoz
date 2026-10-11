import { z } from 'zod';

/** License keys look like EASY-XXXXX-XXXXX-XXXXX (Crockford base32). */
export const licenseKeySchema = z
  .string()
  .trim()
  .toUpperCase()
  .regex(/^EASY-[0-9A-Z]{5}-[0-9A-Z]{5}-[0-9A-Z]{5}$/, 'must look like EASY-XXXXX-XXXXX-XXXXX');

export const activateSchema = z.object({
  key: licenseKeySchema,
});
