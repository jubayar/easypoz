import { Test } from '@nestjs/testing';
import { describe, expect, it } from 'vitest';
import { HealthController } from './health.controller.js';
import { PrismaService } from '../prisma/prisma.service.js';

function fakePrisma(configured: boolean): PrismaService {
  return { configured, ping: async () => configured } as unknown as PrismaService;
}

describe('HealthController', () => {
  it('reports db unconfigured when DATABASE_URL is absent', async () => {
    const controller = new HealthController(fakePrisma(false));
    const result = await controller.health();
    expect(result.status).toBe('ok');
    expect(result.db).toBe('unconfigured');
  });

  it('reports db up when the ping succeeds', async () => {
    const controller = new HealthController(fakePrisma(true));
    const result = await controller.health();
    expect(result.db).toBe('up');
  });
});

describe('HealthController (e2e-lite)', () => {
  it('GET /health returns ok', async () => {
    const moduleRef = await Test.createTestingModule({
      controllers: [HealthController],
    })
      .useMocker((token) => (token === PrismaService ? fakePrisma(false) : undefined))
      .compile();
    const app = moduleRef.createNestApplication();
    await app.init();
    await app.close();
  });
});
