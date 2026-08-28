package com.kh.midpoint.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// 카카오/Tmap 외부 API 응답 캐싱용(같은 좌표·이름 조회가 API 쿼터를 반복해서 쓰지 않게).
// GenericJacksonJsonRedisSerializer는 Jackson 2용(GenericJackson2JsonRedisSerializer)이
// 아니라 Jackson 3(tools.jackson) 전용 클래스다 - 이 프로젝트는 Jackson 2 databind가
// 클래스패스에 없어서 반드시 이쪽을 써야 한다.
//
// 나중에 방/참여자 상태처럼 "캐시"가 아니라 직접 읽고 쓰는 저장소로도 Redis를 쓰려면,
// spring-boot-starter-data-redis가 이미 있으니 StringRedisTemplate(또는
// RedisTemplate<String, Object>)을 주입받아 opsForValue()/opsForHash() 등으로 바로 쓰면
// 된다 - Spring Boot가 spring.data.redis.host/port 설정만으로 자동으로 빈을 만들어준다.
// 이 클래스의 @Cacheable용 캐시매니저와는 별개로 동작한다.
@Configuration
@EnableCaching
public class RedisConfig {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
				.enableSpringCacheNullValueSupport()
				.enableUnsafeDefaultTyping()
				.build();

		RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(DEFAULT_TTL)
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(cacheConfig).build();
	}
}
