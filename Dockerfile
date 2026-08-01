# Multi-stage build — JDK 21 으로 build, JRE 21 Alpine 으로 run.

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gateway-domain gateway-domain
COPY gateway-application gateway-application
COPY gateway-adapter-in gateway-adapter-in
COPY gateway-adapter-out gateway-adapter-out
COPY gateway-bootstrap gateway-bootstrap
RUN ./gradlew :gateway-bootstrap:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# OCI image labels — 레지스트리/스캐너/SBOM 도구가 출처와 라이선스를 읽을 수 있게.
LABEL org.opencontainers.image.title="graphql-gateway" \
      org.opencontainers.image.description="9개 portfolio service 의 REST API 를 GraphQL 한 endpoint 로 묶는 BFF 게이트웨이" \
      org.opencontainers.image.source="https://github.com/ssa1004/graphql-gateway" \
      org.opencontainers.image.url="https://github.com/ssa1004/graphql-gateway" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="ssa1004"

# base 이미지에 박힌 OS 패키지의 패치된 보안 업데이트를 끌어온다 — Trivy 가 잡는
# fixable HIGH/CRITICAL (예: openssl/libcrypto3/libssl3) 를 마스킹 없이 실제로 해소한다.
RUN apk upgrade --no-cache

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
