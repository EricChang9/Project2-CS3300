# Build a single Cloud Run image with React served by Spring Boot.
FROM node:22-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.16-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/backend/target/roommate-expenses-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
