FROM eclipse-temurin:25-jdk AS build

WORKDIR /repo

COPY proto ./proto
COPY ai-connector-service/gradlew ai-connector-service/gradlew.bat ai-connector-service/build.gradle ai-connector-service/settings.gradle ai-connector-service/gradle.properties ./ai-connector-service/
COPY ai-connector-service/gradle ./ai-connector-service/gradle
COPY ai-connector-service/src ./ai-connector-service/src

WORKDIR /repo/ai-connector-service

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /repo/ai-connector-service/build/libs/*.jar app.jar

EXPOSE 1001 1002

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
