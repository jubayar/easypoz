import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { TokenService } from '../auth/token.service.js';

/** A demoted device's pushes stay accepted this long (design D5 grace window). */
export const DEVICE_GRACE_MS = 72 * 60 * 60 * 1000;

/**
 * One active tablet per shop: registering demotes every other active device
 * (recording demotedAt so the sync endpoints can honour the grace window)
 * and returns a device-scoped token pair.
 */
@Injectable()
export class DeviceService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly tokens: TokenService,
  ) {}

  async register(shopId: string, deviceName?: string) {
    const db = this.prisma.require();

    const demoted = await db.device.updateMany({
      where: { shopId, active: true },
      data: { active: false, demotedAt: new Date() },
    });

    const device = await db.device.create({
      data: { shopId, name: deviceName ?? '' },
    });

    const tokens = this.tokens.issueTokens(shopId, device.id);
    return {
      deviceId: device.id,
      demotedCount: demoted.count,
      graceMs: DEVICE_GRACE_MS,
      ...tokens,
    };
  }

  /**
   * Sync-auth rule: the device must be active, or demoted within the grace
   * window so its queued ops still upload before it disappears for good.
   */
  async canSync(shopId: string, deviceId: string): Promise<boolean> {
    const device = await this.prisma.require().device.findFirst({
      where: { id: deviceId, shopId },
    });
    if (!device) return false;
    if (device.active) return true;
    return (
      device.demotedAt !== null &&
      Date.now() - device.demotedAt.getTime() < DEVICE_GRACE_MS
    );
  }
}
