# Build the Linux backend image. The Linux profile disables the in-process Windows worker.
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S posture && adduser -S -G posture -h /app posture
WORKDIR /app
COPY --from=build --chown=posture:posture /src/target/endpoint-posture-java-0.1.0-SNAPSHOT.jar /app/app.jar
USER posture
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]