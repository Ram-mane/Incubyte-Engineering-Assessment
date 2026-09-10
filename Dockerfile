# syntax=docker/dockerfile:1

# Build and runtime are separate images so the deployed artefact carries a JRE and a jar,
# not a JDK, Maven, and the whole dependency cache.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not re-download
# the internet on every deploy.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Spotless, Checkstyle, PMD, JaCoCo and the tests are gates, and CI is where they run.
# Repeating them here would double every deploy for no extra signal - and a deploy that
# builds a jar CI never verified is the actual risk, which is why deploys follow CI.
RUN ./mvnw -B -q package \
      -DskipTests \
      -Dspotless.check.skip=true \
      -Dcheckstyle.skip=true \
      -Dpmd.skip=true \
      -Djacoco.skip=true

FROM eclipse-temurin:21-jre-alpine AS runtime

# Never root: a container process that does not need write access to its own filesystem
# should not have it.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/target/salary-management-*.jar app.jar
USER app

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM reads the container limit, so the
# same image behaves correctly on a 512MB free instance and on a larger one.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
