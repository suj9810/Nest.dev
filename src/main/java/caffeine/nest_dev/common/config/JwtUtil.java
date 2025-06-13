package caffeine.nest_dev.common.config;

import caffeine.nest_dev.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private static final String BLACKLIST_AT_PREFIX = "blacklist_AT:";
    private static final String BLACKLIST_RT_PREFIX = "blacklist_RT:";
    private final StringRedisTemplate stringRedisTemplate;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration, // 60분
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration, // 7일
            StringRedisTemplate stringRedisTemplate) {

        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String createTempAccessToken(User user) {
        long now = System.currentTimeMillis();
        long tempExpiration = 5 * 60 * 1000L; // 5분
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("key", "TEMP")
                .issuedAt(new Date(now))
                .expiration(new Date(now + tempExpiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createAccessToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userRole", user.getUserRole().name())
                .issuedAt(new Date(now)) // 토큰 생성 시간
                .expiration(new Date(now + accessTokenExpiration)) // 만료 시간 : 60분
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpiration)) // 만료 시간 : 7일
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            JwtParser parser = Jwts.parser().verifyWith(key).build();
            parser.parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있습니다.");
        }
        return false;
    }

    // 토큰에서 userId 가져오기
    public Long getUserIdFromToken(String token) {
        JwtParser parser = Jwts.parser().verifyWith(key).build();
        Jws<Claims> claimsJws = parser.parseSignedClaims(token);
        String subject = claimsJws.getPayload().getSubject();
        return Long.parseLong(subject);
    }

    // 만료 시간 계산
    public long getRemainingExpiration(String token) {
        Date expiration = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        return expiration.getTime() - System.currentTimeMillis();
    }

    // 블랙리스트에 refreshToken 추가 메서드
    public void addToBlacklistRefreshToken(String refreshToken) {
        log.info("블랙리스트에 토큰 추가 메서드 시작");
        long remainingExpiration = getRemainingExpiration(refreshToken);
        if (remainingExpiration > 0) {
            stringRedisTemplate.opsForValue()
                    .set(BLACKLIST_RT_PREFIX + refreshToken, "logout",
                            Duration.ofMillis(remainingExpiration));
        }
    }

    // 블랙리스트에 AccessToken 추가 메서드
    public void addToBlacklistAccessToken(String accessToken) {
        log.info("블랙리스트에 토큰 추가 메서드 시작");
        long remainingExpiration = getRemainingExpiration(accessToken);
        if (remainingExpiration > 0) {
            stringRedisTemplate.opsForValue()
                    .set(BLACKLIST_AT_PREFIX + accessToken, "logout",
                            Duration.ofMillis(remainingExpiration));
        }
    }

    // "Bearer" 부분 자르기
    public String resolveToken(String accessToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            String substring = accessToken.substring(7); // "Bearer " 이후 문자열만 추출
            return substring.trim(); // 앞뒤 공백 제거
        }
        return null;
    }
}
