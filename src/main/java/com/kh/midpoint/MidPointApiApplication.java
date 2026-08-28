package com.kh.midpoint;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.kh.midpoint")
@EnableScheduling
public class MidPointApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MidPointApiApplication.class, args);
	}

}
