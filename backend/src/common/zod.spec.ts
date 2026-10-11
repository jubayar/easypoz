import { BadRequestException } from '@nestjs/common';
import { describe, expect, it } from 'vitest';
import { ZodValidationPipe } from './zod.js';
import { z } from 'zod';

const schema = z.object({
  identifier: z.string().min(3),
  code: z.string().regex(/^\d{6}$/, 'must be a 6-digit code'),
});

describe('ZodValidationPipe', () => {
  it('passes valid bodies through', () => {
    const pipe = new ZodValidationPipe(schema);
    expect(pipe.transform({ identifier: 'owner@shop.my', code: '123456' })).toEqual({
      identifier: 'owner@shop.my',
      code: '123456',
    });
  });

  it('returns field-level errors on malformed bodies', () => {
    const pipe = new ZodValidationPipe(schema);
    try {
      pipe.transform({ identifier: 'x', code: 'abc' });
      expect.unreachable('should have thrown');
    } catch (error) {
      const response = (error as BadRequestException).getResponse() as {
        errors: { field: string; message: string }[];
      };
      const fields = response.errors.map((e) => e.field);
      expect(fields).toContain('identifier');
      expect(fields).toContain('code');
    }
  });
});
