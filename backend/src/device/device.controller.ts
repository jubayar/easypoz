import { Body, Controller, Post, UseGuards, UsePipes } from '@nestjs/common';
import { CurrentShop } from '../auth/current-shop.decorator.js';
import { JwtAuthGuard } from '../auth/jwt.guard.js';
import { ZodValidationPipe } from '../common/zod.js';
import { DeviceService } from './device.service.js';
import { registerDeviceSchema } from './device.schemas.js';

@Controller('device')
@UseGuards(JwtAuthGuard)
export class DeviceController {
  constructor(private readonly devices: DeviceService) {}

  @Post('register')
  @UsePipes(new ZodValidationPipe(registerDeviceSchema))
  register(@CurrentShop() shop: { sub: string }, @Body() body: { deviceName?: string }) {
    return this.devices.register(shop.sub, body.deviceName);
  }
}
