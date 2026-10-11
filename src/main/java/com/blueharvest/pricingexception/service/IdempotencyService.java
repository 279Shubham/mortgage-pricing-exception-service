package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.IdempotencyRecord;
import com.blueharvest.pricingexception.repository.IdempotencyRecordRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotencyService {

	private final IdempotencyRecordRepository records;

	private final TransactionTemplate transaction;

	private final JsonMapper json;

	private final Clock clock;

	private final Duration ttl;

	public IdempotencyService(IdempotencyRecordRepository records, TransactionTemplate transaction, JsonMapper json,
			Clock clock, @Value("${pricing.idempotency.ttl}") Duration ttl) {
		this.records = records;
		this.transaction = transaction;
		this.json = json;
		this.clock = clock;
		this.ttl = ttl;
	}

	public StoredResponse execute(IdempotencyScope scope, Object command, Supplier<IdempotentResult> action) {
		String fingerprint = fingerprint(command);
		try {
			return transaction.execute(status -> claimAndRun(scope, fingerprint, action));
		}
		catch (KeyAlreadyClaimed claimed) {
			return replay(scope, fingerprint);
		}
	}

	private StoredResponse claimAndRun(IdempotencyScope scope, String fingerprint,
			Supplier<IdempotentResult> action) {
		Instant now = clock.instant();
		IdempotencyRecord claim = claim(
				new IdempotencyRecord(scope.userId(), scope.route(), scope.key(), fingerprint, now.plus(ttl)));
		IdempotentResult result = action.get();
		claim.complete(result.status(), result.location(), json.writeValueAsString(result.body()));
		return toStoredResponse(claim);
	}

	private IdempotencyRecord claim(IdempotencyRecord record) {
		try {
			return records.saveAndFlush(record);
		}
		catch (DataIntegrityViolationException duplicate) {
			throw new KeyAlreadyClaimed();
		}
	}

	private StoredResponse replay(IdempotencyScope scope, String fingerprint) {
		IdempotencyRecord original = records
			.findByUserIdAndRouteAndIdempotencyKey(scope.userId(), scope.route(), scope.key())
			.orElseThrow(() -> new ConflictException("A request with this Idempotency-Key is still in progress"));
		if (!original.matches(fingerprint)) {
			throw new IdempotencyKeyReusedException();
		}
		return toStoredResponse(original);
	}

	private static StoredResponse toStoredResponse(IdempotencyRecord record) {
		return new StoredResponse(record.getResponseStatus(), record.getResponseLocation(), record.getResponseBody());
	}

	private String fingerprint(Object command) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(json.writeValueAsBytes(command)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private static final class KeyAlreadyClaimed extends RuntimeException {

	}

}
