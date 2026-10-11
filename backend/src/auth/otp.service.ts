import { Injectable, UnauthorizedException } from '@nestjs/common';
import { createHash, randomInt } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service.js';

const CODE_TTL_MS = 5 * 60 * 1000;
const MAX_ATTEMPTS = 5;

/**
 * Issues and verifies 6-digit one-time codes against otp_verifications.
 * Codes are stored hashed; delivery is pluggable — dev logs the code and
 * returns it when OTP_DEBUG=1, a provider plugs in at the send() call site.
 */
@Injectable()
export class OtpService {
  constructor(private readonly prisma: PrismaService) {}

  async issue(identifier: string): Promise<{ expiresAt: Date; devCode?: string }> {
    const code = String(randomInt(0, 1_000_000)).padStart(6, '0');
    const expiresAt = new Date(Date.now() + CODE_TTL_MS);
    // Invalidate any earlier unconsumed codes for this identifier.
    await this.prisma.require().otpVerification.updateMany({
      where: { identifier, consumedAt: null },
      data: { consumedAt: new Date() },
    });
    await this.prisma.require().otpVerification.create({
      data: { identifier, codeHash: this.hash(code), expiresAt },
    });
    this.send(identifier, code);
    return { expiresAt, devCode: this.debugEnabled() ? code : undefined };
  }

  /**
   * Verifies and consumes the newest active code for the identifier.
   * Throws UnauthorizedException on wrong/expired/exhausted codes.
   */
  async verify(identifier: string, code: string): Promise<void> {
    const record = await this.prisma
      .require()
      .otpVerification.findFirst({
        where: { identifier, consumedAt: null },
        orderBy: { createdAt: 'desc' },
      });

    const fail = async () => {
      if (record) {
        await this.prisma.require().otpVerification.update({
          where: { id: record.id },
          data: { attempts: { increment: 1 } },
        });
      }
      throw new UnauthorizedException('invalid or expired code');
    };

    if (!record) throw new UnauthorizedException('invalid or expired code');
    if (record.expiresAt.getTime() < Date.now()) await fail();
    if (record.attempts >= MAX_ATTEMPTS) await fail();
    if (record.codeHash !== this.hash(code)) await fail();

    await this.prisma.require().otpVerification.update({
      where: { id: record.id },
      data: { consumedAt: new Date() },
    });
  }

  private hash(code: string): string {
    return createHash('sha256').update(code).digest('hex');
  }

  private debugEnabled(): boolean {
    return process.env.OTP_DEBUG === '1' || process.env.NODE_ENV !== 'production';
  }

  /** Provider hook — replace with SMS/email sender in production. */
  private send(identifier: string, code: string): void {
    if (this.debugEnabled()) {
      console.log(`[otp] ${identifier} -> ${code}`);
    }
  }
}
