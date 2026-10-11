import { z } from 'zod';

/** Normalized phone (digits, optional leading +) or lowercased email. */
export const identifierSchema = z
  .string()
  .trim()
  .min(3, 'identifier too short')
  .transform((value) =>
    value.includes('@')
      ? value.toLowerCase()
      : value.replace(/[\s()-]/g, ''),
  )
  .refine(
    (value) =>
      value.includes('@')
        ? /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)
        : /^\+?\d{7,15}$/.test(value),
    'must be a valid phone number or email',
  );

export const registerSchema = z.object({
  identifier: identifierSchema,
  shopName: z.string().trim().max(120).optional(),
});

export const verifySchema = z.object({
  identifier: identifierSchema,
  code: z.string().trim().regex(/^\d{6}$/, 'must be a 6-digit code'),
  shopName: z.string().trim().max(120).optional(),
});

export const loginSchema = z.object({
  identifier: identifierSchema,
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(20),
});
