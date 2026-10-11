package com.blueharvest.pricingexception.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@AnalyzeClasses(packages = "com.blueharvest.pricingexception", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule timeComesFromInjectedClock = noClasses()
		.should().callMethod(Instant.class, "now")
		.orShould().callMethod(LocalDateTime.class, "now")
		.orShould().callMethod(OffsetDateTime.class, "now")
		.because("time must come from the injected Clock so tests can control it");

	@ArchTest
	static final ArchRule systemClockOnlyInConfig = noClasses()
		.that().resideOutsideOfPackage("..config..")
		.should().callMethod(Clock.class, "systemUTC")
		.orShould().callMethod(Clock.class, "systemDefaultZone")
		.orShould().callMethod(Clock.class, "tickMillis", ZoneId.class)
		.because("the single system Clock bean is defined in the config package");

}
