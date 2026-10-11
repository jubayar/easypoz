import { Body, Controller, Post, UsePipes } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { ZodValidationPipe } from '../common/zod.js';
import { AuthService } from './auth.service.js';
import { TokenService } from './token.service.js';
import {
  loginSchema,
  refreshSchema,
  registerSchema,
  verifySchema,
} from './auth.schemas.js';

@Controller('auth')
export class AuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly tokens: TokenService,
  ) {}

  /** Register step 1 — also used for login; account is created at verify. */
  @Post('register')
  @Throttle({ strict: { ttl: 60_000, limit: 5 } })
  @UsePipes(new ZodValidationPipe(registerSchema))
  register(@Body() body: { identifier: string; shopName?: string }) {
    return this.auth.requestCode(body.identifier);
  }

  /** Register/login step 2 — verifies the OTP and returns tokens. */
  @Post('verify')
  @Throttle({ strict: { ttl: 60_000, limit: 10 } })
  @UsePipes(new ZodValidationPipe(verifySchema))
  verify(@Body() body: { identifier: string; code: string; shopName?: string }) {
    return this.auth.verifyCode(body.identifier, body.code, body.shopName);
  }

  /** Login — identical to register step 1 (OTP to an existing identifier). */
  @Post('login')
  @Throttle({ strict: { ttl: 60_000, limit: 5 } })
  @UsePipes(new ZodValidationPipe(loginSchema))
  login(@Body() body: { identifier: string }) {
    return this.auth.requestCode(body.identifier);
  }

  /** Rotates the token pair from a valid refresh token. */
  @Post('refresh')
  @Throttle({ strict: { ttl: 60_000, limit: 30 } })
  @UsePipes(new ZodValidationPipe(refreshSchema))
  refresh(@Body() body: { refreshToken: string }) {
    const payload = this.tokens.verifyRefresh(body.refreshToken);
    return this.tokens.issueTokens(payload.sub, payload.deviceId);
  }
}
