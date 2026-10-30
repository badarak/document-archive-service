# syntax=docker/dockerfile:1

# Base images are pinned by digest, so a build gets the same bytes until Dependabot proposes newer ones.
FROM eclipse-temurin:21-jdk-noble@sha256:bd6ac06e2a2df928af21c75c4a8a0f2829a93377a65761d6a577bb32281e2ac3 AS build
WORKDIR /workspace

# The Maven Wrapper checks the checksum of the zip distribution, which it only downloads when unzip is available.
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

# The build files first: their layer stays cached while only the sources change.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY domain/pom.xml domain/
COPY infrastructure/pom.xml infrastructure/
COPY report/pom.xml report/
COPY domain/src domain/src
COPY infrastructure/src infrastructure/src

# The CI runs the tests before building the image. The jar is split into layers, from the least to the most changing.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl infrastructure -am -DskipTests package \
    && cp infrastructure/target/infrastructure-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# No shell nor package manager, and a non-root user: the probes and the logs are the way to look inside.
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:ba6c75fda24af8a9aee3ec7633bd7369ee390c054d4e4292d52f17014cc9a11a
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Numeric, so that Kubernetes can check runAsNonRoot.
USER 65532:65532

# The heap follows the memory limit of the container and leaves a quarter to the rest of the process. On an
# OutOfMemoryError the process stops, to be restarted, instead of running on in an unknown state.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "org.springframework.boot.loader.launch.JarLauncher"]
