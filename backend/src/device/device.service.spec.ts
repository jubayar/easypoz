import { JwtService } from '@nestjs/jwt';
import { describe, expect, it } from 'vitest';
import { DeviceService } from './device.service.js';
import { TokenService } from '../auth/token.service.js';
import { PrismaService } from '../prisma/prisma.service.js';

type DeviceRow = {
  id: string;
  shopId: string;
  name: string;
  active: boolean;
  demotedAt: Date | null;
};

function fixtures() {
  const devices: DeviceRow[] = [];
  const db = {
    device: {
      updateMany: async ({
        where,
        data,
      }: {
        where: { shopId: string; active: boolean };
        data: { active: boolean; demotedAt: Date };
      }) => {
        let count = 0;
        devices.forEach((d) => {
          if (d.shopId === where.shopId && d.active === where.active) {
            Object.assign(d, data);
            count++;
          }
        });
        return { count };
      },
      create: async ({ data }: { data: { shopId: string; name: string } }) => {
        const device: DeviceRow = {
          id: `dev-${devices.length + 1}`,
          name: data.name,
          shopId: data.shopId,
          active: true,
          demotedAt: null,
        };
        devices.push(device);
        return device;
      },
      findFirst: async ({ where }: { where: { id: string; shopId: string } }) =>
        devices.find((d) => d.id === where.id && d.shopId === where.shopId) ?? null,
    },
  };
  const tokens = new TokenService(new JwtService({ secret: 'test' }));
  const service = new DeviceService({ require: () => db } as unknown as PrismaService, tokens);
  return { devices, service, tokens };
}

describe('DeviceService', () => {
  it('registers the first device as active', async () => {
    const f = fixtures();
    const result = await f.service.register('shop-1', 'Counter tablet');
    expect(result.demotedCount).toBe(0);
    expect(result.deviceId).toBe('dev-1');
    expect(f.devices[0].active).toBe(true);
  });

  it('demotes the previous device when a new one registers', async () => {
    const f = fixtures();
    await f.service.register('shop-1', 'old tablet');
    const second = await f.service.register('shop-1', 'new tablet');
    expect(second.demotedCount).toBe(1);
    expect(f.devices[0].active).toBe(false);
    expect(f.devices[0].demotedAt).toBeInstanceOf(Date);
    expect(f.devices[1].active).toBe(true);
  });

  it('issues a device-scoped token', async () => {
    const f = fixtures();
    const result = await f.service.register('shop-1');
    const payload = f.tokens.verifyAccess(result.accessToken);
    expect(payload.deviceId).toBe(result.deviceId);
    expect(payload.sub).toBe('shop-1');
  });

  it('allows sync for active devices, within grace, and never after', async () => {
    const f = fixtures();
    await f.service.register('shop-1', 'old');
    const second = await f.service.register('shop-1', 'new');

    expect(await f.service.canSync('shop-1', second.deviceId)).toBe(true);
    // demoted but within the 72h grace window
    expect(await f.service.canSync('shop-1', 'dev-1')).toBe(true);

    f.devices[0].demotedAt = new Date(Date.now() - 73 * 60 * 60 * 1000);
    expect(await f.service.canSync('shop-1', 'dev-1')).toBe(false);
    expect(await f.service.canSync('shop-1', 'missing')).toBe(false);
  });
});
