# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml first to fetch and cache dependencies (speeding up rebuilds)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build package (skipping tests for speed and size constraints)
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Render web service port
EXPOSE 8080

# Run Spring Boot app.
# Note: Memory options can be specified in Render's environment variables if upgraded,
# but under the free tier (512MB RAM), Stanford CoreNLP will gracefully degrade and 
# route analysis to the lightweight Regex Fallback engine to prevent OOM.
ENTRYPOINT ["java", "-jar", "app.jar"]
