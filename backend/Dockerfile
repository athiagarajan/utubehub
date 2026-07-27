# Multi-stage Docker build for Spring Boot 3 Backend
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src src

RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
