FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/ignite-kafka-consumer-*.jar /app/ignite-kafka-consumer.jar

# Set environment variables with defaults
ENV KAFKA_BOOTSTRAP_SERVERS=kafka-service:9092
ENV KAFKA_TOPIC=dbserver1.public.customers
ENV KAFKA_GROUP_ID=ignite-consumer-group
ENV IGNITE_ADDRESS=ignite-service:10800

# Run the application
ENTRYPOINT ["java", "-jar", "/app/ignite-kafka-consumer.jar"]
