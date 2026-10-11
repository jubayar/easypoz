import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module.js';
import { DeviceModule } from '../device/device.module.js';
import { SyncController } from './sync.controller.js';
import { SyncService } from './sync.service.js';

@Module({
  imports: [AuthModule, DeviceModule],
  controllers: [SyncController],
  providers: [SyncService],
})
export class SyncModule {}
