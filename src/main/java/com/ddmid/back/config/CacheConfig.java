package com.ddmid.back.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 식당 검색처럼 자주 안 바뀌는 카카오 API 응답을 Redis에 캐싱해서 같은 좌표로
 * 반복 조회할 때 외부 API 호출을 줄인다. 캐시별 TTL(만료 시간)을 여기서 관리한다.
 *
 * CachingConfigurer로 errorHandler를 지정하는 이유: Redis가 아직 로컬에 안 떠 있거나
 * 잠깐 응답이 없을 때, 기본 동작은 예외를 그대로 던져서 캐싱과 무관한 API 응답 전체가
 * 500 에러가 나버린다. 캐시는 "있으면 좋은" 최적화일 뿐이므로, 캐시 조회/저장이 실패하면
 * 로그만 남기고 원래 메서드를 그대로 호출해서(캐시 미스처럼) 서비스가 계속 동작하게 한다.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

	private static final String NEARBY_RESTAURANTS_CACHE = "nearbyRestaurants";
	private static final Duration NEARBY_RESTAURANTS_TTL = Duration.ofHours(24);

	@Bean
	public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

		return builder -> builder
				.withCacheConfiguration(NEARBY_RESTAURANTS_CACHE, defaultConfig.entryTtl(NEARBY_RESTAURANTS_TTL));
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
				log.warn("캐시 조회 실패({}), 원래 API를 호출합니다: {}", cache.getName(), exception.toString());
			}

			@Override
			public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
				log.warn("캐시 저장 실패({}): {}", cache.getName(), exception.toString());
			}

			@Override
			public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
				log.warn("캐시 삭제 실패({}): {}", cache.getName(), exception.toString());
			}

			@Override
			public void handleCacheClearError(RuntimeException exception, Cache cache) {
				log.warn("캐시 초기화 실패({}): {}", cache.getName(), exception.toString());
			}
		};
	}
}
