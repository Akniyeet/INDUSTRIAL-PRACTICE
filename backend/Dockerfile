# syntax=docker/dockerfile:1.6
# =====================================================================
# Webizon backend — multi-stage image
# ---------------------------------------------------------------------
#  Stage 1 (build)   : Maven + Eclipse Temurin JDK 21
#  Stage 2 (runtime) : Eclipse Temurin JRE 21 (Ubuntu Jammy), non-root
# ---------------------------------------------------------------------
# The runtime image is intentionally NOT distroless — we want a shell
# for diagnostics in production (jstack, jcmd, curl for healthchecks).
# It still runs as a non-root user and contains no build tooling.
# =====================================================================

# ---------- build stage ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

# Leverage Docker layer cache: resolve dependencies before copying sources.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Create an unprivileged user.
RUN groupadd --system --gid 10001 webizon \
 && useradd  --system --uid 10001 --gid webizon --home-dir /app --shell /usr/sbin/nologin webizon \
 && mkdir -p /app \
 && chown -R webizon:webizon /app

WORKDIR /app

COPY --from=build --chown=webizon:webizon /workspace/target/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENV JAVA_OPTS="" \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:+ExitOnOutOfMemoryError \
                       -XX:+HeapDumpOnOutOfMemoryError \
                       -XX:HeapDumpPath=/tmp \
                       -Djava.security.egd=file:/dev/urandom \
                       -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
