package com.ddmid.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 식당 검색처럼 자주 안 바뀌는 카카오 API 응답을 Redis에 캐싱해서 같은 좌표로
 * 반복 조회할 때 외부 API 호출을 줄인다. 캐시별 TTL(만료 시간)을 여기서 관리한다.
 */
@Configuration
public class CacheConfig {

	private static final String NEARBY_RESTAURANTS_CACHE = "nearbyRestaurants";
	private static final Duration NEARBY_RESTAURANTS_TTL = Duration.ofHours(24);

	@Bean
	public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

		return builder -> builder
				.withCacheConfiguration(NEARBY_RESTAURANTS_CACHE, defaultConfig.entryTtl(NEARBY_RESTAURANTS_TTL));
	}
}
