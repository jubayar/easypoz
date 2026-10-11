import { BadRequestException, ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { LicenseSigner } from './license.signer.js';

export interface LicenseStatusView {
  status: 'TRIAL' | 'ACTIVE' | 'EXPIRED';
  trialDaysLeft?: number;
  expiresAt: string | null;
  signedPayload: string;
}

@Injectable()
export class LicenseService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly signer: LicenseSigner,
  ) {}

  /** Effective state for a shop: ACTIVE license > trial countdown > expired. */
  async status(shopId: string): Promise<LicenseStatusView> {
    const db = this.prisma.require();

    const active = await db.license.findFirst({
      where: { shopId, status: 'ACTIVE', expiresAt: { gt: new Date() } },
      orderBy: { expiresAt: 'desc' },
    });
    if (active?.expiresAt) {
      return this.sign(shopId, 'ACTIVE', active.expiresAt.toISOString());
    }

    const trial = await db.license.findFirst({
      where: { shopId, status: 'TRIAL', trialStartedAt: { not: null } },
      orderBy: { trialStartedAt: 'desc' },
    });
    if (trial?.trialStartedAt) {
      const trialDays = trial.trialDays ?? 14;
      const msLeft =
        trial.trialStartedAt.getTime() + trialDays * 24 * 60 * 60 * 1000 - Date.now();
      const daysLeft = Math.max(0, Math.ceil(msLeft / (24 * 60 * 60 * 1000)));
      const expiresAt = new Date(
        trial.trialStartedAt.getTime() + trialDays * 24 * 60 * 60 * 1000,
      );
      return {
        status: 'TRIAL',
        trialDaysLeft: daysLeft,
        expiresAt: expiresAt.toISOString(),
        signedPayload: this.signer.sign({
          shopId,
          status: 'TRIAL',
          expiresAt: expiresAt.toISOString(),
          issuedAt: new Date().toISOString(),
        }),
      };
    }

    return this.sign(shopId, 'EXPIRED', null);
  }

  private sign(
    shopId: string,
    status: 'ACTIVE' | 'EXPIRED',
    expiresAt: string | null,
  ): LicenseStatusView {
    return {
      status,
      expiresAt,
      signedPayload: this.signer.sign({
        shopId,
        status,
        expiresAt,
        issuedAt: new Date().toISOString(),
      }),
    };
  }

  /** Binds an issued key to this shop and activates the subscription period. */
  async activate(shopId: string, rawKey: string): Promise<LicenseStatusView> {
    const key = rawKey.trim().toUpperCase();
    const db = this.prisma.require();

    const licenseKey = await db.licenseKey.findUnique({ where: { key } });
    if (!licenseKey) throw new NotFoundException('unrecognized license key');
    if (licenseKey.boundToShopId && licenseKey.boundToShopId !== shopId) {
      throw new ConflictException('license key is already used by another shop');
    }
    if (licenseKey.boundToShopId === shopId) {
      // Idempotent re-activation of this shop's own key.
      return this.status(shopId);
    }

    const now = new Date();
    const expiresAt = new Date(now.getTime() + licenseKey.planDays * 24 * 60 * 60 * 1000);

    const existingRow = await db.license.findFirst({ where: { shopId } });
    await db.$transaction(async (tx) => {
      await tx.licenseKey.update({
        where: { key },
        data: { boundToShopId: shopId, boundAt: now },
      });
      if (existingRow) {
        await tx.license.update({
          where: { id: existingRow.id },
          data: { status: 'ACTIVE', activatedAt: now, expiresAt },
        });
      } else {
        await tx.license.create({
          data: { shopId, status: 'ACTIVE', activatedAt: now, expiresAt },
        });
      }
    });

    const view = await this.status(shopId);
    if (view.status !== 'ACTIVE') throw new BadRequestException('activation failed');
    return view;
  }
}
