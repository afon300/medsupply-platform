FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY warehouse-rpc-contract warehouse-rpc-contract
COPY warehouse-rpc-service warehouse-rpc-service
RUN ./gradlew :warehouse-rpc-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/warehouse-rpc-service/build/libs/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
