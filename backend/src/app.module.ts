import { MiddlewareConsumer, Module, type NestModule } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule } from '@nestjs/config';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import helmet from 'helmet';
import { HealthModule } from './health/health.module.js';
import { PrismaModule } from './prisma/prisma.module.js';
import { AuthModule } from './auth/auth.module.js';
import { DeviceModule } from './device/device.module.js';
import { LicenseModule } from './license/license.module.js';
import { SyncModule } from './sync/sync.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    // Global baseline rate limit; named 'strict' profile guards auth endpoints.
    ThrottlerModule.forRoot([
      { ttl: 60_000, limit: 300 },
      { name: 'strict', ttl: 60_000, limit: 10 },
    ]),
    PrismaModule,
    AuthModule,
    DeviceModule,
    LicenseModule,
    SyncModule,
    HealthModule,
  ],
  providers: [{ provide: APP_GUARD, useClass: ThrottlerGuard }],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    // Applied here (not main.ts) so test harnesses get identical security headers.
    consumer.apply(helmet()).forRoutes('*');
  }
}
