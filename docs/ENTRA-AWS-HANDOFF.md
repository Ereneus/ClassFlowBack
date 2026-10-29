# Guia de traspaso: Microsoft Entra ID y AWS

Esta guia resume lo implementado en `ClassFlow-Back`, lo que falta configurar y el orden recomendado para que otro equipo complete Microsoft Entra ID y el despliegue en AWS.

## Estado actual

Ultimo commit de referencia: `ded4821`.

El backend mantiene dos modos de seguridad:

- `local`: login y JWT propio para desarrollo y transicion.
- `entra`: resource server que valida access tokens de Microsoft Entra ID.

Sin un perfil activo, el API Gateway, BFF y `ms-auth` usan Entra por defecto. Docker Compose y Kubernetes activan `docker,entra` en esos servicios; el modo `local` requiere seleccion explicita. El frontend MSAL se mantiene en un repositorio separado y debe configurarse para solicitar un access token de esta API.

## Arquitectura esperada

```text
Frontend React
    |
    | access_token de Microsoft Entra
    v
AWS API Gateway (JWT Authorizer)
    |
    v
API Gateway Spring en EC2
    |
    +--> BFF
    +--> ms-auth
    +--> ms-academic
    +--> ms-assistance
    +--> ms-message
    +--> ms-notification
```

El API Gateway de Spring sigue siendo el gateway interno de ClassFlow. AWS API Gateway sera la frontera publica.

## Cambios ya realizados

### Base de datos

Se agrego `V7__add_external_identities.sql` en `ms-auth`.

La tabla `user_identities` relaciona una identidad externa con el usuario interno:

```text
user_id
provider
external_subject  # oid de Entra
tenant_id         # tid de Entra
created_at
updated_at
```

La restriccion unica es:

```text
provider + tenant_id + external_subject
```

El `users.id` no se reemplaza. Las relaciones existentes de alumnos, apoderados, cursos, notas, asistencia y mensajes siguen usando el ID interno.

### Vinculacion

`ExternalIdentityService`:

1. Busca la identidad por proveedor, tenant y subject.
2. Si existe, recupera el usuario interno.
3. Si no existe, busca un usuario existente por correo y vincula la identidad.
4. Si no encuentra coincidencia, rechaza el acceso.
5. No crea usuarios automaticamente ni asigna roles por su cuenta.

### Endpoint de perfil

```text
GET /api/auth/me
```

Con autenticacion local usa el usuario autenticado actual. Con Entra usa `oid`, `tid` y `preferred_username` o `email` para resolver el perfil interno.

### Seguridad Entra

`ms-auth`, el BFF y el API Gateway Spring tienen perfiles `entra` que validan:

- Firma del token mediante las claves publicas de Microsoft.
- `issuer`.
- `audience`.
- Expiracion.
- Scopes y App Roles.

En `ms-auth`, el perfil `entra` deshabilita login, registro, recuperacion y cambio de contrasena locales.

## Variables de configuracion

Configurar en `.env` para Compose o en la ConfigMap de Kubernetes:

```env
ENTRA_ISSUER_URI=https://login.microsoftonline.com/<TENANT_ID>/v2.0
ENTRA_API_AUDIENCE=<API_CLIENT_ID>
```

El issuer debe coincidir con la version de token que emite la app de la API (claim `ver`). Actualmente la API emite tokens v1, por lo que `.env.example` y la ConfigMap usan `https://sts.windows.net/<TENANT_ID>/` (con la barra final). Si se configura el manifiesto a v2 (ver tareas de Entra), volver a `https://login.microsoftonline.com/<TENANT_ID>/v2.0`.

La SPA debe configurarse en su propio proyecto con su `FRONTEND_CLIENT_ID`, el scope `api://<API_CLIENT_ID>/access_as_user` y una redirect URI registrada para ese frontend. El backend no necesita el client ID de la SPA.

Compose activa los perfiles Entra por defecto. Para sobrescribirlos explicitamente:

```env
GATEWAY_SPRING_PROFILES_ACTIVE=docker,entra
BFF_SPRING_PROFILES_ACTIVE=docker,entra
MS_AUTH_SPRING_PROFILES_ACTIVE=docker,entra
```

Los tres servicios reciben `ENTRA_ISSUER_URI` y `ENTRA_API_AUDIENCE`. `ms-auth` expone `/api/auth/me` para obtener el perfil local y `/api/auth/validate` para que los microservicios validen el access token de Entra y resuelvan el usuario interno.

## Tareas del equipo de Entra ID

1. Registrar la API de ClassFlow en Microsoft Entra.
2. Configurar el Application ID URI.
3. Exponer el scope `access_as_user`.
   En el manifiesto de la app de la API, definir `"accessTokenAcceptedVersion": 2` (o `requestedAccessTokenVersion: 2` en el manifiesto nuevo). Sin esto Entra emite tokens v1 con issuer `https://sts.windows.net/<TENANT_ID>/`, que no coincide con `ENTRA_ISSUER_URI` (`.../v2.0`) y todo responde `401`.
4. Registrar el frontend como SPA y configurar `http://localhost:3000` como redirect URI de desarrollo.
6. Crear App Roles para `Administrator`, `Teacher`, `Student` y `Guardian`.
7. Asignar usuarios o grupos a esos roles.
7. Configurar `email` o `preferred_username` como optional claim del access token de la API. `ms-auth` lo necesita para vincular por primera vez el `oid`/`tid` con el correo de un usuario interno existente.
8. Entregar al equipo frontend y backend:
   - `TENANT_ID`.
   - `FRONTEND_CLIENT_ID`.
   - `API_CLIENT_ID`.
   - Scope completo de la API.
   - Redirect URI final.

No se deben compartir secretos en los repositorios.

## Tareas del equipo AWS

1. Desplegar los contenedores del backend en EC2.
2. Configurar PostgreSQL segun la estrategia acordada para la EC2.
3. Publicar el endpoint que recibira AWS API Gateway.
4. Crear un AWS API Gateway HTTP API, preferentemente con JWT Authorizer.
5. Configurar:
   - Issuer: `https://login.microsoftonline.com/<TENANT_ID>/v2.0`.
   - Audience: `API_CLIENT_ID`.
   - Ruta proxy hacia el API Gateway Spring.
6. Configurar HTTPS, dominio, Security Groups y CORS.
7. No exponer publicamente las bases de datos ni los puertos internos de microservicios.
8. Configurar las variables Entra en los servicios `api-gateway`, `bff` y `ms-auth`.

El BFF y el API Gateway Spring validan nuevamente el token aunque AWS API Gateway ya lo haya validado. Es intencional y cumple defensa en profundidad.

## Pruebas de aceptacion

### Backend

- Token valido con issuer correcto: `200`.
- Token de otro tenant: `401`.
- Audience incorrecta: `401`.
- Token expirado: `401`.
- Usuario Entra asociado a un usuario existente: devuelve su perfil y relaciones.
- Usuario Entra sin coincidencia local: acceso rechazado hasta vincularlo.
- Usuarios y relaciones existentes permanecen intactos.

### Operacion local

El modo local se mantiene para desarrollo:

```bash
docker compose up --build
```

Para Entra, completar `.env` con el client ID SPA y el scope de API. Para ejecutar un servicio Java directamente, definir `ENTRA_ISSUER_URI` y `ENTRA_API_AUDIENCE` en el entorno.

## Decisiones que no deben cambiarse sin revisar

- No reemplazar `users.id` por el `oid` de Entra.
- No eliminar usuarios actuales ni sus relaciones.
- No usar nombres como identificador.
- No confiar en un rol enviado por el frontend.
- No guardar access tokens en el repositorio ni en archivos versionados.
- No permitir registro local cuando el perfil `entra` este activo.
