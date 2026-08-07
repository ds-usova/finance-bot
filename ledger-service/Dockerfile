FROM eclipse-temurin:25-jdk AS build

WORKDIR /repo

COPY proto ./proto
COPY openapi ./openapi
COPY ledger-service/gradlew ledger-service/gradlew.bat ledger-service/build.gradle ledger-service/settings.gradle ledger-service/gradle.properties ./ledger-service/
COPY ledger-service/gradle ./ledger-service/gradle
COPY ledger-service/src ./ledger-service/src

WORKDIR /repo/ledger-service

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /repo/ledger-service/build/libs/*.jar app.jar

EXPOSE 1000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
