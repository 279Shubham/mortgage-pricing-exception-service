# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-noble AS source
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY config/ config/
COPY src/ src/

FROM source AS test
RUN --mount=type=cache,target=/root/.gradle ./gradlew check --no-daemon --console=plain

FROM source AS build
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --console=plain \
 && java -Djarmode=tools -jar build/libs/application.jar extract --layers --destination /extracted

FROM eclipse-temurin:21-jre-noble AS runtime
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
