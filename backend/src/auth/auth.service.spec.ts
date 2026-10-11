import { JwtService } from '@nestjs/jwt';
import { describe, expect, it } from 'vitest';
import { AuthService } from './auth.service.js';
import { OtpService } from './otp.service.js';
import { TokenService } from './token.service.js';
import { PrismaService } from '../prisma/prisma.service.js';

const SECRET = 'test-secret';

function tokenService() {
  return new TokenService(new JwtService({ secret: SECRET }));
}

// ── TokenService ────────────────────────────────────────────────────────────

describe('TokenService', () => {
  it('round-trips access and refresh tokens', () => {
    const tokens = tokenService();
    const issued = tokens.issueTokens('shop-1', 'device-1');
    const access = tokens.verifyAccess(issued.accessToken);
    expect(access.sub).toBe('shop-1');
    expect(access.deviceId).toBe('device-1');
    expect(access.typ).toBe('access');

    const refresh = tokens.verifyRefresh(issued.refreshToken);
    expect(refresh.sub).toBe('shop-1');
    expect(refresh.typ).toBe('refresh');
  });

  it('rejects a refresh token presented as access', () => {
    const tokens = tokenService();
    const issued = tokens.issueTokens('shop-1');
    expect(() => tokens.verifyAccess(issued.refreshToken)).toThrowError();
  });

  it('rejects tokens signed with a different secret', () => {
    const other = tokenService();
    const issued = other.issueTokens('shop-1');
    const tokens = new TokenService(new JwtService({ secret: 'other-secret' }));
    expect(() => tokens.verifyAccess(issued.accessToken)).toThrowError();
  });
});

// ── OtpService ──────────────────────────────────────────────────────────────

type OtpRow = {
  id: string;
  identifier: string;
  codeHash: string;
  attempts: number;
  expiresAt: Date;
  consumedAt: Date | null;
  createdAt: Date;
};

function otpFixtures() {
  const rows: OtpRow[] = [];
  const db = {
    otpVerification: {
      create: async ({ data }: { data: Partial<Omit<OtpRow, 'id' | 'createdAt'>> }) => {
        rows.push({
          id: `id-${rows.length + 1}`,
          createdAt: new Date(),
          attempts: 0,
          consumedAt: null,
          ...data,
        } as OtpRow);
      },
      findFirst: async ({ where }: { where: { identifier: string; consumedAt: null } }) =>
        [...rows].reverse().find((r) => r.identifier === where.identifier && !r.consumedAt) ?? null,
      update: async ({ where, data }: { where: { id: string }; data: Record<string, unknown> }) => {
        const row = rows.find((r) => r.id === where.id)!;
        const increment = (data as { attempts?: { increment: number } }).attempts;
        if (increment) row.attempts += increment.increment;
        else Object.assign(row, data);
      },
      updateMany: async ({ where, data }: { where: { identifier: string; consumedAt: null }; data: Record<string, unknown> }) => {
        rows.forEach((r) => {
          if (r.identifier === where.identifier && !r.consumedAt) Object.assign(r, data);
        });
      },
    },
  };
  return { db, rows };
}

describe('OtpService', () => {
  it('verifies a freshly issued code exactly once', async () => {
    const { db, rows } = otpFixtures();
    const service = new OtpService({ require: () => db } as unknown as PrismaService);
    const { devCode } = await service.issue('+60123456789');
    expect(devCode).toMatch(/^\d{6}$/);
    expect(rows).toHaveLength(1);

    await service.verify('+60123456789', devCode!);
    // Second use of the same code fails (consumed).
    await expect(service.verify('+60123456789', devCode!)).rejects.toThrowError();
  });

  it('rejects a wrong code and increments attempts', async () => {
    const { db, rows } = otpFixtures();
    const service = new OtpService({ require: () => db } as unknown as PrismaService);
    await service.issue('a@b.com');
    await expect(service.verify('a@b.com', '000000')).rejects.toThrowError();
    expect(rows[0].attempts).toBe(1);
  });

  it('rejects an expired code', async () => {
    const { db, rows } = otpFixtures();
    const service = new OtpService({ require: () => db } as unknown as PrismaService);
    const { devCode } = await service.issue('a@b.com');
    rows[0].expiresAt = new Date(Date.now() - 1000);
    await expect(service.verify('a@b.com', devCode!)).rejects.toThrowError();
  });
});

// ── AuthService.verifyCode ──────────────────────────────────────────────────

function authFixtures() {
  const accounts: { id: string; email?: string; phone?: string; name: string }[] = [];
  const licenses: { shopId: string; status: string }[] = [];
  const otp = otpFixtures();
  const db = {
    otpVerification: otp.db.otpVerification,
    shopAccount: {
      findFirst: async ({ where }: { where: { email?: string; phone?: string } }) =>
        accounts.find((a) => a.email === where.email || a.phone === where.phone) ?? null,
      create: async ({ data }: { data: { email?: string; phone?: string; name: string } }) => {
        const account = { id: `shop-${accounts.length + 1}`, ...data };
        accounts.push(account);
        return account;
      },
    },
    license: {
      create: async ({ data }: { data: { shopId: string; status: string } }) => {
        licenses.push(data);
      },
    },
  };
  const prisma = { require: () => db } as unknown as PrismaService;
  const auth = new AuthService(prisma, new OtpService(prisma), tokenService());
  return { db, accounts, licenses, auth, otpRows: otp.rows };
}

describe('AuthService.verifyCode', () => {
  it('creates the account + trial license on first verification', async () => {
    const f = authFixtures();
    const otpService = new OtpService({ require: () => f.db } as unknown as PrismaService);
    const { devCode } = await otpService.issue('owner@shop.my');
    const auth = new AuthService(
      { require: () => f.db } as unknown as PrismaService,
      otpService,
      tokenService(),
    );

    const result = await auth.verifyCode('owner@shop.my', devCode!, 'Kedai Jaya');
    expect(result.isNewAccount).toBe(true);
    expect(f.accounts).toHaveLength(1);
    expect(f.accounts[0].email).toBe('owner@shop.my');
    expect(f.licenses).toEqual([
      { shopId: expect.any(String), status: 'TRIAL', trialStartedAt: expect.any(Date) },
    ]);
    expect(result.accessToken).toBeTruthy();
  });

  it('returns the existing account on later logins without a new license', async () => {
    const f = authFixtures();
    const otpService = new OtpService({ require: () => f.db } as unknown as PrismaService);
    const auth = new AuthService(
      { require: () => f.db } as unknown as PrismaService,
      otpService,
      tokenService(),
    );
    const first = await auth.verifyCode('owner@shop.my', (await otpService.issue('owner@shop.my')).devCode!, 'Kedai Jaya');
    const second = await auth.verifyCode('owner@shop.my', (await otpService.issue('owner@shop.my')).devCode!);

    expect(second.isNewAccount).toBe(false);
    expect(second.shopId).toBe(first.shopId);
    expect(f.licenses).toHaveLength(1);
  });
});
