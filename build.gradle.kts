import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
	java
	pmd
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.blueharvest"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("io.rest-assured:rest-assured:6.1.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

pmd {
	toolVersion = "7.24.0"
	ruleSetFiles = files("config/pmd/ruleset.xml")
	isConsoleOutput = true
}

tasks.pmdTest {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = TestExceptionFormat.FULL
	}
}

tasks.jar {
	enabled = false
}

tasks.bootJar {
	archiveFileName = "application.jar"
}
