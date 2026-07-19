FROM gradle:9.6.1-jdk21 AS build
WORKDIR /workspace
COPY backend/gradle gradle
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/src src
RUN gradle --no-daemon --console=plain --info bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache && addgroup -S srm && adduser -S srm -G srm
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER srm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
