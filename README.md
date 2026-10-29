# ClassFlow Back

Sobre el Proyecto
-------
Proyecto backend compuesto por microservicios Spring Boot para gestionar funcionalidades de un sistema escolar (autenticación, académico, asistencia, mensajería, notificaciones, gateway y BFF). Orquestado con `docker-compose` para despliegue local mediante contenedores PostgreSQL por servicio.

Frontend
--------
La carpeta `frontend/` contiene la SPA React/Vite integrada con Microsoft Entra ID mediante MSAL.

Ejecución conjunta con el frontend
-----------------------------------
Completa `.env` a partir de `.env.example`, incluyendo `VITE_ENTRA_CLIENT_ID` con el client ID de la aplicación SPA registrada en Entra, y levanta el stack desde la raíz:

```bash
docker compose up --build
```

Arquitectura y servicios
------------------------
- [api-gateway](api-gateway/README.md) (puerto 8080): API Gateway (Spring Cloud Gateway).
- [ms-auth](ms-auth/README.md) (puerto 8081): Autenticación y gestión de usuarios (JWT + Spring Security).
- [ms-academic](ms-academic/README.md) (puerto 8082): Cursos, asignaturas, evaluaciones, notas.
- [ms-assistance](ms-assistance/README.md) (puerto 8083): Asistencia y anotaciones.
- [ms-message](ms-message/README.md) (puerto 8084): Mensajes y anuncios.
- [ms-notification](ms-notification/README.md) (puerto 8085): Envío de emails/alertas.
- [bff](bff/README.md) (puerto 8086): Backend-for-Frontend con endpoints agregados (dashboard).

Estructura de Carpetas
---------------------
```
ClassFlow-Back/
├── .env                               # Variables de entorno (JWT, API keys, etc.)
├── docker-compose.yml                 # Orquestación de servicios y bases de datos
├── README.md                          # Este archivo
│
├── api-gateway/                       # Spring Cloud Gateway (puerto 8080)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/gateway/
│       ├── GatewayServiceApplication.java
│       ├── config/                    # CorsConfig, SwaggerConfig
│       ├── security/                  # SecurityConfig (filtro JWT)
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
├── bff/                               # Backend-for-Frontend (puerto 8086)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/bff/
│       ├── BffServiceApplication.java
│       ├── controller/                # DashboardController
│       ├── service/                   # DashboardService
│       ├── dto/                       # DashboardResponse
│       ├── config/                    # WebClientConfig, SwaggerConfig
│       ├── security/                  # SecurityConfig (filtro JWT)
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
├── ms-auth/                           # Autenticación y usuarios (puerto 8081)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/auth/
│       ├── AuthServiceApplication.java
│       ├── controller/                # AuthController (login, register, validate...)
│       ├── service/                   # AuthService, UserService, PasswordResetService
│       ├── repository/                # UserRepository
│       ├── entity/                    # User, Role
│       ├── dto/                       # LoginRequestDTO, LoginResponseDTO, etc.
│       ├── security/                  # JwtTokenProvider, SecurityConfig
│       ├── config/                    # SwaggerConfig
│       ├── exception/                 # GlobalExceptionHandler, ErrorResponse
│       └── resources/db/migration/    # 6 migraciones Flyway (V1–V6)
│
├── ms-academic/                       # Cursos, evaluaciones, notas (puerto 8082)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/academic/
│       ├── AcademicServiceApplication.java
│       ├── controller/                # 4 controladores
│       ├── service/                   # 4 servicios
│       ├── repository/                # 4 repositorios
│       ├── entity/                    # Course, Evaluation, Grade, Subject
│       ├── dto/                       # CourseDTO, EvaluationDTO, GradeDTO, SubjectDTO
│       ├── config/                    # SwaggerConfig
│       ├── security/                  # JwtAuthenticationFilter, SecurityConfig
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
├── ms-assistance/                     # Asistencia y anotaciones (puerto 8083)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/assistance/
│       ├── AssistanceServiceApplication.java
│       ├── controller/                # AttendanceController, AnnotationController
│       ├── service/                   # AttendanceService, AnnotationService
│       ├── repository/                # 2 repositorios
│       ├── entity/                    # Attendance, Annotation
│       ├── dto/                       # AttendanceDTO, AnnotationDTO, etc.
│       ├── config/                    # SwaggerConfig
│       ├── security/                  # JwtAuthenticationFilter, SecurityConfig
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
├── ms-message/                        # Mensajes y anuncios (puerto 8084)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/message/
│       ├── MessageServiceApplication.java
│       ├── controller/                # MessageController, AnnouncementController
│       ├── service/                   # MessageService, AnnouncementService
│       ├── repository/                # 2 repositorios
│       ├── entity/                    # Message, Announcement
│       ├── dto/                       # MessageDTO, AnnouncementDTO, etc.
│       ├── config/                    # SwaggerConfig
│       ├── security/                  # JwtAuthenticationFilter, SecurityConfig
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
├── ms-notification/                   # Notificaciones (puerto 8085)
│   ├── pom.xml, Dockerfile, mvnw
│   └── src/main/java/com/ohiggins/classflow/notification/
│       ├── NotificationServiceApplication.java
│       ├── controller/                # NotificationController
│       ├── service/                   # NotificationService, EmailService
│       ├── repository/                # NotificationRepository
│       ├── entity/                    # Notification
│       ├── dto/                       # EmailRequestDTO, AlertRequestDTO, etc.
│       ├── enums/                     # NotificationType
│       ├── config/                    # SwaggerConfig
│       ├── security/                  # JwtAuthenticationFilter, SecurityConfig
│       └── exception/                 # GlobalExceptionHandler, ErrorResponse
│
└── k8s/                               # Manifiestos de Kubernetes
    ├── namespace.yml, secret.yml, configmap.yml
    ├── api-gateway.yml, bff.yml, frontend.yml
    ├── ms-*.yml                       # Un deployment por microservicio
    ├── ingress-traefik*.yml
    └── databases/                     # Manifiestos de las 5 bases de datos
```

Archivos clave
-------------
- Orquestación: [docker-compose.yml](docker-compose.yml#L1-L200)
- Configuración por servicio: `application.yml` / `application.properties` y `application-docker.yml` (ver carpetas de cada servicio).
- Migraciones Flyway: `src/main/resources/db/migration` en cada servicio.

Requisitos
----------
- JDK 25
- Maven (o usar `./mvnw` incluido)
- Docker and Docker Compose (para ejecutar con contenedores)

Obtener el código
-----------------

**Opción 1: Clonar desde GitHub**
```bash
git clone https://github.com/theseventhshadow/ClassFlow-Back.git
cd ClassFlow-Back
```

**Opción 2: Descargar como ZIP**
1. Ve a https://github.com/theseventhshadow/ClassFlow-Back
2. Haz clic en el botón verde "Code"
3. Selecciona "Download ZIP"
4. Extrae el archivo en tu directorio de trabajo
5. Abre la carpeta en tu editor (VS Code, IntelliJ, etc.)

Inicio rápido (desarrollo con Maven)
----------------------------------
Desde la raíz de cada servicio (ej. `ms-auth`) ejecutar:

```bash
./mvnw spring-boot:run
```

Inicio rápido (con Docker Compose)
---------------------------------
Levanta todos los servicios y bases de datos PostgreSQL definidos en `docker-compose.yml`:

```bash
docker compose up --build
```

Sin un perfil activo, el gateway, BFF y `ms-auth` usan Entra por defecto. Compose activa `docker,entra` en esos servicios; los demás microservicios conservan `docker` y validan el access token a través de `ms-auth`.

Preparacion para Microsoft Entra ID
-----------------------------------
Para ejecutar el backend con Microsoft Entra ID, configura `.env` a partir de `.env.example`. Compose activa los perfiles Entra por defecto en gateway, BFF y `ms-auth`; el issuer y audience son obligatorios también al ejecutar esos servicios directamente:

```bash
docker compose up --build
```

El perfil `entra` valida firma, issuer, expiración y audience del access token usando las claves públicas de Microsoft. El frontend obtiene el access token mediante MSAL y solicita el scope configurado en `VITE_ENTRA_API_SCOPE`.

El modo local solo se activa mediante una selección explícita del perfil `local`; no es el modo predeterminado.

Bases de datos y migraciones
---------------------------
- En desarrollo los servicios usan H2 en memoria. En Docker se usan contenedores Postgres dedicados por servicio (p. ej. `auth-db`).
- Flyway está habilitado y las migraciones se almacenan en `src/main/resources/db/migration` (archivos `V1__...`, `V2__...`, ...). Revisar dichos archivos para el esquema y datos seed.

Endpoints principales (resumen)
------------------------------

- Auth (`/api/auth`): en modo local, `POST /login`, `POST /register`, `GET /validate` y gestion de usuarios; en modo Entra, `GET /me` y `GET /validate` aceptan access tokens de Microsoft. Login, registro y cambio de contrasena locales quedan deshabilitados con Entra.
- Academic (`/api/courses`, `/api/subjects`, `/api/evaluations`, `/api/grades`): CRUD estándar (GET, POST, PUT, DELETE) y consultas por relaciones (ej. `/api/subjects/course/{courseId}`).
- Assistance (`/api/attendance`, `/api/annotations`): registro y consultas por estudiante, curso y fecha.
- Message (`/api/messages`, `/api/announcements`): envío, listado, marcar como leído, anuncios activos.
- Notification (`/api/notifications`): `POST /email`, `POST /alert`, `POST /create`, `GET /user/{userId}`, `PUT /{id}/sent`.
- BFF (`/api/bff`): endpoints de dashboard agregados (p. ej. `/api/bff/dashboard/{userId}`, `/api/bff/dashboard/stats/{userId}`).

Build y creación de imágenes Docker
----------------------------------
- Cada servicio incluye un `Dockerfile` y `mvnw` para construir el JAR y generar la imagen.
- Ejemplo (desde la raíz):

```bash
docker build -t ms-auth:local ./ms-auth
```

Pruebas
------
- Hay clases de prueba en `src/test/java` para cada servicio. Ejecutar pruebas con Maven:

```bash
./mvnw test
```

Consideraciones de seguridad y configuración
-------------------------------------------
- `ms-auth` usa JWT (clave en `application.yml` para desarrollo).
- `ms-notification` incluye parámetros SMTP de ejemplo en `application.properties` — no dejar credenciales en claro.
- Glitchtip (http://localhost:8000) es un servicio de error tracking self-hospedado compatible con Sentry. La primera vez que accedas debes crear una cuenta, organización y proyecto, y copiar el DSN generado a `SENTRY_DSN` en `.env`.

Archivos y referencias rápidas
-----------------------------
- `docker-compose.yml` — orquestación
- `*/pom.xml` — dependencias y Java/Spring Boot
- `*/src/main/resources/application-docker.yml` — configuración para contenedores
- `*/src/main/resources/db/migration` — migraciones Flyway
- `*/src/main/java/*/controller` — controladores y endpoints

Diagramas
---------
Arquitectura (visión general):
<img width="1218" height="523" alt="Captura de pantalla 2026-05-15 163922" src="https://github.com/user-attachments/assets/3803f878-ad8a-4aa0-9678-34e6a7b6e317" />


Secuencia: flujo de autenticación (simplificado)
<img width="1361" height="670" alt="Captura de pantalla 2026-05-15 163802" src="https://github.com/user-attachments/assets/e897da4b-dc04-40fb-9c31-293bbc5121d9" />


Ejemplos prácticos
------------------
Comandos útiles:

```bash
# Construir todos los módulos (desde la raíz)
./mvnw -T 1C -DskipTests package

# Ejecutar todos los servicios en contenedores
docker compose up --build

# Ejecutar un servicio localmente (ej. ms-auth)
cd ms-auth
../mvnw spring-boot:run
```

Ejemplos `curl` (gateway en `localhost:8080` o directamente al servicio por su puerto):

```bash
# 1) Login (Auth) -> obtiene JWT
curl -X POST http://localhost:8081/api/auth/login \
	-H "Content-Type: application/json" \
	-d '{"email":"admin@example.com","password":"password"}'

# 2) Crear usuario (registro)
curl -X POST http://localhost:8081/api/auth/register \
	-H "Content-Type: application/json" \
	-d '{"firstName":"Luis","lastName":"Martín","idNumber":"10000010","email":"luis.martin@example.com","password":"password","role":"TEACHER"}'

# 3) Dashboard BFF (ejemplo de uso con token)
curl -X GET http://localhost:8086/api/bff/dashboard/1 \
	-H "Authorization: Bearer <TOKEN_OBTENIDO>"

# 4) Enviar mensaje (Message Service)
curl -X POST http://localhost:8084/api/messages/send \
	-H "Content-Type: application/json" \
	-d '{"senderId":1,"receiverId":2,"subject":"Prueba","body":"Hola"}'

# 5) Enviar email (Notification Service)
curl -X POST http://localhost:8085/api/notifications/email \
	-H "Content-Type: application/json" \
	-d '{"to":"destino@example.com","subject":"Asunto prueba","body":"Cuerpo del mensaje"}'
```

Notas sobre perfiles y configuración
-----------------------------------
- Para ejecución dentro de Docker, los servicios usan `SPRING_PROFILES_ACTIVE=docker` (configuración en `application-docker.yml`).
- No commits de secretos: sustituir `JWT_SECRET` y credenciales SMTP por variables de entorno o usar un vault.



