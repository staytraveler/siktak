# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle wrapper와 빌드 스크립트를 먼저 복사해 의존성 레이어를 캐시한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 CI(GitHub Actions)에서 수행한다.
RUN ./gradlew bootJar --no-daemon -x test

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 siktak
COPY --from=build /workspace/build/libs/*.jar app.jar
USER siktak

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# Render는 PORT 환경변수를 주입한다 (application-prod.yml: server.port=${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
