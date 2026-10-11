package com.blueharvest.pricingexception.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;

@AnalyzeClasses(packages = "com.blueharvest.pricingexception", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final Set<String> SYSTEM_TIME_METHODS = Set.of("currentTimeMillis", "nanoTime");

	private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("system", "systemUTC", "systemDefaultZone",
			"tick", "tickMillis", "tickSeconds", "tickMinutes");

	@ArchTest
	static final ArchRule timeComesFromInjectedClock = noClasses()
		.should().accessTargetWhere(describe("a java.time now() without a Clock, or System time",
				ArchitectureTest::readsSystemTime))
		.because("time must come from the injected Clock so tests can control it");

	@ArchTest
	static final ArchRule systemClockOnlyInConfig = noClasses()
		.that().resideOutsideOfPackage("..config..")
		.should().accessTargetWhere(describe("a system Clock factory", ArchitectureTest::createsSystemClock))
		.because("the single system Clock bean is defined in the config package");

	@ArchTest
	static final ArchRule noLegacyOrDatabaseTimestamps = noClasses()
		.should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
		.orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
		.orShould().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.annotations.CreationTimestamp")
		.orShould().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.annotations.UpdateTimestamp")
		.orShould().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.annotations.CurrentTimestamp")
		.because("time must come from the injected Clock so tests can control it");

	@ArchTest
	static final ArchRule controllerServiceRepositoryLayers = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Web").definedBy("..web..")
		.layer("Controller").definedBy("..controller..")
		.layer("Service").definedBy("..service..")
		.layer("Repository").definedBy("..repository..")
		.whereLayer("Web").mayNotBeAccessedByAnyLayer()
		.whereLayer("Controller").mayNotBeAccessedByAnyLayer()
		.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Web")
		.whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
		.because("controllers go through services, and only services use repositories");

	@ArchTest
	static final ArchRule onlyServicesUseRepositories = noClasses()
		.that().resideOutsideOfPackages("..service..", "..repository..")
		.should().dependOnClassesThat().resideInAPackage("..repository..")
		.because("only services use the Spring Data repositories");

	private static boolean readsSystemTime(JavaAccess<?> access) {
		AccessTarget target = access.getTarget();
		boolean systemTime = target.getOwner().isEquivalentTo(System.class)
				&& SYSTEM_TIME_METHODS.contains(target.getName());
		return systemTime || isNowWithoutClock(target);
	}

	private static boolean isNowWithoutClock(AccessTarget target) {
		return target instanceof CodeUnitAccessTarget codeUnit
				&& target.getOwner().getPackageName().startsWith("java.time")
				&& target.getName().equals("now")
				&& !codeUnit.getRawParameterTypes().stream().map(JavaClass::getName).toList()
					.equals(List.of(Clock.class.getName()));
	}

	private static boolean createsSystemClock(JavaAccess<?> access) {
		AccessTarget target = access.getTarget();
		return target.getOwner().isAssignableTo(InstantSource.class)
				&& SYSTEM_CLOCK_FACTORIES.contains(target.getName());
	}

}
