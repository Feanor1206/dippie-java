FROM node:22-alpine AS frontend-build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /src/backend/springboot
COPY backend/springboot/mvnw backend/springboot/pom.xml ./
COPY backend/springboot/.mvn ./.mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY backend/springboot/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /src/backend/springboot/target/dippie-backend-*.jar /app/app.jar
COPY --from=frontend-build /src/frontend/dist /app/web
ENV PORT=3000
ENV DIPPIE_WEB_DIST=/app/web
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
