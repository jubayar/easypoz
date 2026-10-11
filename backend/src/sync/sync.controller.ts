import { Body, Controller, Get, Post, Query, UseGuards, UsePipes } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { CurrentShop } from '../auth/current-shop.decorator.js';
import { JwtAuthGuard } from '../auth/jwt.guard.js';
import { ZodValidationPipe } from '../common/zod.js';
import { SyncService } from './sync.service.js';
import { pushBatchSchema } from './journal-op.schemas.js';

@Controller('sync')
@UseGuards(JwtAuthGuard)
export class SyncController {
  constructor(private readonly sync: SyncService) {}

  @Post('push')
  @Throttle({ default: { ttl: 60_000, limit: 120 } })
  @UsePipes(new ZodValidationPipe(pushBatchSchema))
  push(@CurrentShop() shop: { sub: string }, @Body() body: { deviceId: string; ops: unknown[] }) {
    return this.sync.push(shop.sub, body.deviceId, body.ops as never[]);
  }

  @Get('pull')
  @Throttle({ default: { ttl: 60_000, limit: 120 } })
  pull(
    @CurrentShop() shop: { sub: string },
    @Query('since') since: string = '0',
  ) {
    const parsed = Number.parseInt(since, 10);
    if (!Number.isSafeInteger(parsed) || parsed < 0) {
      return { error: 'since must be a non-negative integer' };
    }
    return this.sync.pull(shop.sub, parsed);
  }
}
