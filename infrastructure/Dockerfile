# Dockerfile nhiều giai đoạn cho Dippie:
# 1. Build giao diện React 19 + Vite bằng Node 22.
# 2. Build backend Spring Boot 3.5 / Java 21, giữ cache thư viện Maven.
# 3. Đóng gói runtime JRE 21 Alpine chạy bằng tài khoản thường (không dùng root).

# Giai đoạn 1: build frontend
FROM node:22-alpine AS frontend-build
WORKDIR /src/frontend

# Chép tệp khai báo gói trước để giữ lại cache npm ci khi chỉ sửa mã nguồn
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# Giai đoạn 2: build backend
FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /src/backend/springboot

# Tải trước thư viện Maven để không phải tải lại khi chỉ sửa code Java
COPY backend/springboot/mvnw backend/springboot/pom.xml ./
COPY backend/springboot/.mvn ./.mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B dependency:go-offline

# Bản demo cần H2 trong jar, nên docker-compose.demo.yml truyền MAVEN_PROFILES=demo
ARG MAVEN_PROFILES=
COPY backend/springboot/src ./src
RUN ./mvnw -B package -DskipTests ${MAVEN_PROFILES:+-P${MAVEN_PROFILES}}

# Giai đoạn 3: đóng gói runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Tạo tài khoản dippie không có quyền root và cấp quyền thư mục /app
RUN addgroup -S dippie && adduser -S -G dippie -u 1001 dippie \
    && mkdir -p /app /app/web /app/media /app/cache /app/data \
    && chown -R dippie:dippie /app

COPY --from=backend-build --chown=dippie:dippie /src/backend/springboot/target/dippie-backend-*.jar /app/app.jar
COPY --from=frontend-build --chown=dippie:dippie /src/frontend/dist /app/web

ENV PORT=3000 \
    DIPPIE_WEB_DIST=/app/web \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"

USER dippie

EXPOSE 3000

# Gọi /api/health để kiểm tra cả ứng dụng lẫn kết nối cơ sở dữ liệu
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO /dev/null -T 4 "http://127.0.0.1:${PORT:-3000}/api/health" || exit 1

# Dùng dạng exec để Java nhận thẳng SIGTERM khi dừng container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
