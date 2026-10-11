import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { OtpService } from './otp.service.js';
import { TokenService } from './token.service.js';

/**
 * Register and login are one OTP flow: the identifier receives a code, and
 * verifying it returns tokens — creating the shop account (with tenant config
 * and trial license, per the account-license spec) on first verification.
 */
@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly otp: OtpService,
    private readonly tokens: TokenService,
  ) {}

  /** Step 1 of both register and login: issue an OTP to the identifier. */
  async requestCode(identifier: string) {
    const existing = await this.prisma.require().shopAccount.findFirst({
      where: identifier.includes('@') ? { email: identifier } : { phone: identifier },
    });
    const { expiresAt, devCode } = await this.otp.issue(identifier);
    return { isNewAccount: existing === null, otpExpiresAt: expiresAt, devCode };
  }

  /** Step 2: verify the code, upsert the account, return the token pair. */
  async verifyCode(identifier: string, code: string, shopName?: string) {
    await this.otp.verify(identifier, code);
    const db = this.prisma.require();

    const existing = await db.shopAccount.findFirst({
      where: identifier.includes('@') ? { email: identifier } : { phone: identifier },
    });

    const account =
      existing ??
      (await db.shopAccount.create({
        data: {
          ...(identifier.includes('@') ? { email: identifier } : { phone: identifier }),
          name: shopName ?? '',
          config: { create: {} },
        },
      }));

    if (!existing) {
      // Trial starts when the shop account is created (account-license spec).
      await db.license.create({
        data: { shopId: account.id, status: 'TRIAL', trialStartedAt: new Date() },
      });
    }

    const tokens = this.tokens.issueTokens(account.id);
    return { shopId: account.id, isNewAccount: existing === null, ...tokens };
  }
}
