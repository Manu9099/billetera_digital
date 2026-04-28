# ============================
# Stage 1: Build con Maven
# ============================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copiar pom.xml primero para aprovechar cache de capas
COPY pom.xml .
RUN mvn -f pom.xml dependency:go-offline -q || true

# Copiar el código fuente
COPY src ./src

# Build (skip tests en Docker, correr en CI)
RUN mvn clean package -DskipTests -q

# ============================
# Stage 2: Runtime — imagen mínima
# ============================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Crear usuario no-root (seguridad)
RUN addgroup -S yapeseguro && adduser -S yapeseguro -G yapeseguro

WORKDIR /app

# Copiar el JAR del stage anterior
COPY --from=builder /app/target/*.jar app.jar

# Cambiar owner
RUN chown yapeseguro:yapeseguro app.jar

USER yapeseguro

# Java 21: enable preview features y configuración de memoria
ENTRYPOINT ["java", \
  "--enable-preview", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

EXPOSE 8080
