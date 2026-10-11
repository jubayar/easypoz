import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

export interface ShopTokenPayload {
  sub: string; // shop id
  deviceId?: string;
  typ: 'access' | 'refresh';
}

const ACCESS_TTL_SEC = 12 * 60 * 60; // 12h — short-lived, refreshable
const REFRESH_TTL_SEC = 30 * 24 * 60 * 60; // 30 days (design D9)

/**
 * Issues and verifies the shop-scoped JWT pair. Both tokens are stateless
 * (signed with JWT_SECRET); revocation in v1 is secret rotation, which is
 * acceptable for a single-tablet-per-shop deployment.
 */
@Injectable()
export class TokenService {
  constructor(private readonly jwt: JwtService) {}

  issueTokens(shopId: string, deviceId?: string) {
    const now = Math.floor(Date.now() / 1000);
    return {
      accessToken: this.sign(shopId, deviceId, 'access'),
      accessExpiresAt: new Date((now + ACCESS_TTL_SEC) * 1000),
      refreshToken: this.sign(shopId, deviceId, 'refresh'),
      refreshExpiresAt: new Date((now + REFRESH_TTL_SEC) * 1000),
    };
  }

  verifyAccess(token: string): ShopTokenPayload {
    return this.verify(token, 'access');
  }

  verifyRefresh(token: string): ShopTokenPayload {
    return this.verify(token, 'refresh');
  }

  private sign(shopId: string, deviceId: string | undefined, typ: 'access' | 'refresh') {
    return this.jwt.sign({
      sub: shopId,
      ...(deviceId ? { deviceId } : {}),
      typ,
    } satisfies ShopTokenPayload);
  }

  private verify(token: string, expectedTyp: 'access' | 'refresh'): ShopTokenPayload {
    try {
      const payload = this.jwt.verify<ShopTokenPayload>(token);
      if (payload.typ !== expectedTyp) throw new Error('wrong token type');
      return payload;
    } catch {
      throw new UnauthorizedException(
        expectedTyp === 'access' ? 'invalid access token' : 'invalid refresh token',
      );
    }
  }
}
