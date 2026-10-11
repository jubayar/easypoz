import { createHmac, timingSafeEqual } from 'node:crypto';

export interface SignedLicense {
  shopId: string;
  status: 'TRIAL' | 'ACTIVE' | 'EXPIRED';
  expiresAt: string | null; // ISO
  issuedAt: string; // ISO — server clock, the tablet's rollback anchor
}

/**
 * Signs the license state the tablet caches offline (design D9): the payload
 * carries the server-issued timestamp so a rolled-back device clock cannot
 * extend an expiry without invalidating the signature.
 */
export class LicenseSigner {
  constructor(private readonly secret: string) {}

  sign(payload: SignedLicense): string {
    const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = createHmac('sha256', this.secret).update(body).digest('base64url');
    return `${body}.${signature}`;
  }

  verify(token: string): SignedLicense | null {
    const [body, signature] = token.split('.');
    if (!body || !signature) return null;
    const expected = createHmac('sha256', this.secret).update(body).digest('base64url');
    const a = Buffer.from(signature);
    const b = Buffer.from(expected);
    if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
    try {
      return JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as SignedLicense;
    } catch {
      return null;
    }
  }
}
