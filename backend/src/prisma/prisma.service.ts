import { Injectable, type OnModuleDestroy, type OnModuleInit } from '@nestjs/common';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../generated/prisma/client.js';

/**
 * Owns the Prisma client lifecycle. Boot-tolerant: when DATABASE_URL is unset
 * (fresh checkout, no local Postgres) the service still constructs so the app
 * and /health work; anything that needs the DB must call {@link require} or
 * check {@link configured} first.
 */
@Injectable()
export class PrismaService implements OnModuleInit, OnModuleDestroy {
  private readonly client: PrismaClient | null = process.env.DATABASE_URL
    ? new PrismaClient({ adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }) })
    : null;

  get configured(): boolean {
    return this.client !== null;
  }

  /** The live client; throws with a clear message when no DB is configured. */
  require(): PrismaClient {
    if (!this.client) throw new Error('DATABASE_URL is not configured');
    return this.client;
  }

  /** Cheap connectivity probe for /health; never throws. */
  async ping(): Promise<boolean> {
    if (!this.client) return false;
    try {
      await this.client.$queryRaw`SELECT 1`;
      return true;
    } catch {
      return false;
    }
  }

  async onModuleInit(): Promise<void> {
    await this.client?.$connect();
  }

  async onModuleDestroy(): Promise<void> {
    await this.client?.$disconnect();
  }
}
