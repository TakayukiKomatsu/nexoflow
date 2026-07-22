FROM gradle:9.6.1-jdk21@sha256:9795f8c9009a7de6adea522b907c9aadc38fe7c313874112c2b696c8e70b0303 AS build
WORKDIR /workspace
COPY backend/gradle gradle
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/src src
RUN gradle --no-daemon --console=plain --info bootJar

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
RUN addgroup -S srm && adduser -S srm -G srm
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER srm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
