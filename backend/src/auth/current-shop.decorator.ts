import { createParamDecorator, type ExecutionContext } from '@nestjs/common';
import type { ShopTokenPayload } from './token.service.js';

/** Reads the verified JWT payload attached by JwtAuthGuard. */
export const CurrentShop = createParamDecorator(
  (_data: unknown, context: ExecutionContext): ShopTokenPayload => {
    const request = context.switchToHttp().getRequest();
    if (!request.shop) throw new Error('CurrentShop used without JwtAuthGuard');
    return request.shop;
  },
);
