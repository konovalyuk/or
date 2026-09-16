# Build the browser application.
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Build the Spring Boot application and package the SPA into the JAR.
FROM eclipse-temurin:25-jdk-alpine AS backend-build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
COPY --from=frontend-build /app/frontend/dist ./frontend/dist
RUN chmod +x gradlew && ./gradlew bootJar -x test

# Runtime image contains only the application JAR and a Java runtime.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
