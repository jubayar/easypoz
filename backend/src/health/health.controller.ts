import { Controller, Get } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';

@Controller('health')
export class HealthController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  async health() {
    const db = !this.prisma.configured ? 'unconfigured' : (await this.prisma.ping()) ? 'up' : 'down';
    return { status: 'ok', db, uptimeSec: Math.floor(process.uptime()) };
  }
}
