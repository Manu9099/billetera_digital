# YapeSeguro Backend

Billetera digital con 12 features diferenciadores — construida con Java 21 + Spring Boot 3.3.x + PostgreSQL.

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 (LTS) |
| Framework | Spring Boot 3.3.5 |
| Base de datos | PostgreSQL 16 |
| Cache / Rate Limit | Redis 7 |
| Mensajes async | RabbitMQ 3 |
| ORM | Spring Data JPA / Hibernate 6 |
| Migraciones | Flyway 10 |
| Seguridad | Spring Security 6 + JWT (jjwt 0.12) |
| Mapeo DTO | MapStruct 1.6 |
| QR | ZXing 3.5 |
| Docs API | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5 + Testcontainers |
| Contenedores | Docker + Docker Compose |

## Features

1. **Yape Seguro** — dinero retenido hasta confirmación del comprador (marketplace)
2. **Botón de reclamo rápido** — disputa con evidencia, seguimiento y timeline
3. **Negocios verificados** — perfil con RUC, categoría, ubicación y reseñas
4. **Bolsillos personal/negocio** — dos wallets separadas, analytics de ventas
5. **Mini inventario** — producto, precio, stock, ventas por QR automático
6. **Comprobantes automáticos** — HTML + PDF + QR de validación
7. **Pagos programados** — autopago, notificaciones previas, Job diario
8. **Cuentas grupales** — polladas, viajes, seguimiento de quién pagó
9. **QR monto fijo** — "Consulta dental - S/60" listo para escanear
10. **Ranking de gastos** — snapshot mensual por categoría
11. **Préstamos transparentes** — tasa, mora, total a pagar desde el inicio
12. **Modo adulto mayor** — interfaz simplificada configurada por usuario

## Estructura del proyecto

```
src/main/java/com/yapeseguro/
├── api/
│   ├── controllers/     ← REST Controllers (todos los endpoints)
│   └── exception/       ← GlobalExceptionHandler + custom exceptions
├── application/
│   └── services/        ← Lógica de negocio + Jobs programados
├── domain/
│   ├── entities/        ← Entidades de dominio puras
│   └── enums/           ← Enums de dominio
├── infrastructure/
│   ├── config/          ← SecurityConfig, RedisConfig, RabbitMQConfig
│   ├── persistence/
│   │   └── entities/    ← JPA entities mapeadas a la BD
│   └── security/        ← JwtService + JwtAuthenticationFilter
└── YapeSeguroApplication.java

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__init_schema.sql   ← Schema completo con Flyway
```

## Levantar en desarrollo

### Prerequisitos
- Java 21 (JDK)
- Docker Desktop
- Maven 3.9+

### Pasos

```bash
# 1. Levantar infraestructura (PostgreSQL + Redis + RabbitMQ)
docker compose up postgres redis rabbitmq -d

# 2. Correr la app
./mvnw spring-boot:run

# 3. (Opcional) Levantar todo con Docker
docker compose up --build
```

### URLs
| Servicio | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html |
| RabbitMQ UI | http://localhost:15672 (guest/guest) |
| Actuator | http://localhost:8080/api/v1/actuator/health |

## Variables de entorno requeridas en producción

```env
DATASOURCE_URL=jdbc:postgresql://host:5432/yapeseguro_db
DATASOURCE_USERNAME=postgres
DATASOURCE_PASSWORD=strong_password
REDIS_HOST=redis_host
REDIS_PASSWORD=redis_password
RABBITMQ_HOST=rabbitmq_host
JWT_SECRET=base64_encoded_secret_min_32_chars
CULQI_API_KEY=pk_live_...
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
GOOGLE_CLIENT_ID=...
```

## Endpoints principales

| Método | Ruta | Feature |
|---|---|---|
| POST | /auth/register | Registro |
| POST | /auth/login | Login |
| POST | /auth/google | Google OAuth |
| GET | /wallets/me | Saldo (personal + negocio) |
| POST | /transactions/p2p | Enviar dinero |
| POST | /transactions/marketplace | **Yape Seguro** |
| PATCH | /transactions/{id}/confirm-receipt | Confirmar recepción |
| GET | /transactions/{id}/receipt | Comprobante |
| POST | /disputes | Abrir reclamo |
| POST | /disputes/{id}/evidence | Subir evidencia |
| POST | /business/profile | Crear negocio |
| GET | /business/inventory | Ver inventario |
| POST | /business/inventory | Agregar producto |
| GET | /business/analytics | Analytics negocio |
| POST | /groups | Crear grupo |
| POST | /groups/{id}/pay | Pagar al grupo |
| POST | /scheduled-payments | Crear pago programado |
| POST | /qr | Crear QR monto fijo |
| POST | /qr/{id}/pay | Pagar QR escaneado |
| GET | /analytics/expenses | Ranking de gastos |
