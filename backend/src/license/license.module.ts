import { Global, Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module.js';
import { LicenseController } from './license.controller.js';
import { LicenseService } from './license.service.js';
import { LicenseSigner } from './license.signer.js';

export const LICENSE_SIGNER = new LicenseSigner(
  process.env.LICENSE_SIGNING_KEY ?? process.env.JWT_SECRET ?? 'dev-only-secret-change-me',
);

@Global()
@Module({
  imports: [AuthModule],
  controllers: [LicenseController],
  providers: [{ provide: LicenseSigner, useValue: LICENSE_SIGNER }, LicenseService],
  exports: [LicenseSigner, LicenseService],
})
export class LicenseModule {}
