# Multi-stage build to compile and package the Spring Boot application using Java 25
# Stage 1: Build stage
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper configurations and pom
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Make wrapper script executable
RUN chmod +x mvnw

# Resolve dependencies offline to speed up subsequent builds
RUN ./mvnw dependency:go-offline -B

# Copy the source code and build the package
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Run as a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar file from the build stage
COPY --from=build /app/target/MinecraftModCatalog-0.0.1-SNAPSHOT.jar app.jar

# Expose server port (Render dynamically allocates $PORT env variable, default to 8080)
EXPOSE 8080

# Configure JVM memory optimizations
ENTRYPOINT ["java", "-jar", "app.jar"]
