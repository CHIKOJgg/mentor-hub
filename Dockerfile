# =============================================================================
# Stage 1: Build React SPA Frontend
# =============================================================================
FROM node:24-alpine AS frontend-builder
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# =============================================================================
# Stage 2: Build Spring Boot Application with Bundled Frontend
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy backend sources
COPY src ./src

# Copy built frontend into Spring Boot static resources
COPY --from=frontend-builder /workspace/frontend/dist ./src/main/resources/static/

# Compile and package monolithic application jar
RUN mvn -B clean package

# =============================================================================
# Stage 3: Minimal Production JRE 21 Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=production
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
