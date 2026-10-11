package com.blueharvest.pricingexception.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {

	static final Instant START = Instant.parse("2026-01-15T09:00:00Z");

	private volatile Instant now = START;

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}

	void reset() {
		now = START;
	}

	void advance(Duration duration) {
		now = now.plus(duration);
	}

}
