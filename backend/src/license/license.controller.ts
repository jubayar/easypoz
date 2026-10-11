import { Body, Controller, Get, Post, UseGuards, UsePipes } from '@nestjs/common';
import { CurrentShop } from '../auth/current-shop.decorator.js';
import { JwtAuthGuard } from '../auth/jwt.guard.js';
import { ZodValidationPipe } from '../common/zod.js';
import { LicenseService } from './license.service.js';
import { activateSchema } from './license.schemas.js';

@Controller('license')
@UseGuards(JwtAuthGuard)
export class LicenseController {
  constructor(private readonly licenses: LicenseService) {}

  @Get('status')
  status(@CurrentShop() shop: { sub: string }) {
    return this.licenses.status(shop.sub);
  }

  @Post('activate')
  @UsePipes(new ZodValidationPipe(activateSchema))
  activate(@CurrentShop() shop: { sub: string }, @Body() body: { key: string }) {
    return this.licenses.activate(shop.sub, body.key);
  }
}
