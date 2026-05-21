# Multi-stage build — JDK 21 으로 build, JRE 21 Alpine 으로 run.

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gateway-domain gateway-domain
COPY gateway-application gateway-application
COPY gateway-adapter-in gateway-adapter-in
COPY gateway-adapter-out gateway-adapter-out
COPY gateway-bootstrap gateway-bootstrap
RUN ./gradlew :gateway-bootstrap:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# non-root user (k8s Pod Security Standards restricted 호환).
RUN addgroup -g 10001 app && adduser -u 10001 -G app -s /bin/sh -D app
USER 10001:10001

# bootJar 는 classifier 'boot' — gateway-bootstrap-*-boot.jar.
COPY --from=builder /build/gateway-bootstrap/build/libs/*-boot.jar app.jar

EXPOSE 8080

# 컨테이너 메모리 인식 + G1GC. OOM 시 즉시 종료해 k8s 가 재시작하게 한다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
