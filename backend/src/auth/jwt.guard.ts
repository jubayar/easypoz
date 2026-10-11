import {
  type CanActivate,
  type ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import type { Request } from 'express';
import { TokenService, type ShopTokenPayload } from './token.service.js';

export interface ShopRequest extends Request {
  shop?: ShopTokenPayload;
}

/** Protects shop-scoped routes; attaches the payload as `req.shop`. */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly tokens: TokenService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<ShopRequest>();
    const header = request.headers.authorization;
    if (!header?.startsWith('Bearer ')) throw new UnauthorizedException('missing bearer token');
    request.shop = this.tokens.verifyAccess(header.slice('Bearer '.length));
    return true;
  }
}
