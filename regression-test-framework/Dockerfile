FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1000 appuser

COPY --from=builder /build/target/regression-test-framework-*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

ENV SERVER_PORT=8085 \
    POSTGRES_HOST=postgres-regression \
    POSTGRES_DB=regression_db \
    POSTGRES_USER=regression \
    POSTGRES_PASSWORD=regression \
    KAFKA_BOOTSTRAP_SERVERS=kafka:9092

EXPOSE 8085

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
