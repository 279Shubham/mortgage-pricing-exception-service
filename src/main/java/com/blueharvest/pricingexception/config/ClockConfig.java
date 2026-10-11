package com.blueharvest.pricingexception.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.tickMillis(ZoneOffset.UTC);
	}

}
