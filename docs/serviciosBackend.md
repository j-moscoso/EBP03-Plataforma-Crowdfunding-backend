# Servicios Backend

## Base técnica

El backend usa Spring Boot 4.1.1, Spring WebMVC, Spring Data JPA, Spring Security y PostgreSQL. Flyway ejecuta las migraciones versionadas desde `src/main/resources/db/migration`; Hibernate usa `ddl-auto=validate`, por lo que no modifica el esquema silenciosamente. Las pruebas usan H2 en memoria con el mismo script de migración.

La autenticación usa sesiones opacas: el token completo solo viaja en una cookie `AUTH_SESSION` con `HttpOnly`, `SameSite=Lax` y `Secure` configurable. En `sessions` solo se almacena su SHA-256. Las contraseñas se almacenan con BCrypt.

Base URL local: `http://localhost:8080`

## Modelo público

```json
{
  "user": {
    "id": "UUID",
    "name": "Nombre público",
    "email": "correo normalizado",
    "role": "creator|sponsor",
    "verificationStatus": "pending|verified"
  },
  "session": {
    "expiresAt": "2026-01-01T00:00:00Z"
  }
}
```

Nunca se devuelven `passwordHash`, `tokenHash`, contraseñas, tokens completos, secretos OAuth ni otros secretos.

## Endpoints de autenticación

### Flujo de prueba con sesión

Los endpoints protegidos no funcionan abriendo la URL sin más en el navegador. Primero hay que ejecutar `POST /api/auth/register` o `POST /api/auth/login`; la respuesta establece la cookie HttpOnly `AUTH_SESSION`. Después, el mismo cliente HTTP debe enviar esa cookie al llamar `GET /api/auth/me` o cualquier endpoint protegido.

Si se abre `GET /api/auth/me` sin haber iniciado sesión, la respuesta esperada es `401 Unauthorized`. Esto es intencional: el endpoint no debe devolver el perfil de otro usuario ni crear una sesión implícita.

En las rutas con `{draftId}` o `{campaignId}`, las llaves indican un marcador de documentación. Deben reemplazarse por el UUID real devuelto al crear el borrador o publicar la campaña. Enviar literalmente `{campaignId}` responde `400 INVALID_PARAMETER`.

### 1) Registro

`POST /api/auth/register`

Parámetros:

```json
{
  "name": "Ana",
  "email": " ana@example.com ",
  "password": "Secure1!",
  "role": "creator"
}
```

Qué hace:
- normaliza el correo (`trim` + lowercase)
- valida la contraseña con política fuerte
- crea el usuario y genera una sesión
- responde con el usuario público + sesión
- setea la cookie `AUTH_SESSION` en la respuesta

Respuesta `201`:

```json
{
  "user": {
    "id": "UUID",
    "name": "Ana",
    "email": "ana@example.com",
    "role": "creator",
    "verificationStatus": "pending"
  },
  "session": {
    "expiresAt": "2026-01-01T00:00:00Z"
  }
}
```

Errores principales:
- `400` si la validación falla o la contraseña es débil
- `409` si el correo ya existe

### 2) Inicio de sesión

`POST /api/auth/login`

Body:

```json
{
  "email": "example.sponsor@impulsafund.test",
  "password": "Secure1!",
  "rememberMe": true
}
```

Qué hace:
- valida credenciales
- verifica si la cuenta está activa
- crea una nueva sesión
- envía nueva cookie `AUTH_SESSION`

Respuesta `200`:

```json
{
  "user": {
    "id": "UUID",
    "name": "Example Sponsor",
    "email": "example.sponsor@impulsafund.test",
    "role": "sponsor",
    "verificationStatus": "verified"
  },
  "session": {
    "expiresAt": "2026-01-01T00:00:00Z"
  }
}
```

Error de credenciales:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Las credenciales no son validas.",
  "details": []
}
```

### 3) Login social

`POST /api/auth/social`

Body:

```json
{
  "provider": "google",
  "authorizationCode": "codigo-devuelto-por-google",
  "role": "sponsor"
}
```

Qué hace:
- intercambia el `authorizationCode` con el proveedor OAuth configurado
- obtiene la identidad del usuario desde Google
- crea o reusa la cuenta del usuario
- genera sesión autenticada

Respuesta `201`: mismo formato que `register` / `login` con `user` y `session`.

### 4) Perfil actual

`GET /api/auth/me`

Requiere la cookie `AUTH_SESSION` válida.

Respuesta `200`:

```json
{
  "id": "UUID",
  "name": "Ana",
  "email": "ana@example.com",
  "role": "creator",
  "verificationStatus": "pending"
}
```

### 5) Cierre de sesión

`POST /api/auth/logout`

No requiere sesión vigente para permitir reintentos idempotentes.

Qué hace:
- lee la cookie `AUTH_SESSION`
- revoca la sesión asociada
- elimina la cookie del cliente
- responde `204 No Content`

## Endpoints de campañas y borradores

### 1) Crear borrador

`POST /api/drafts`

Requiere sesión de creador autenticado.

Body:

```json
{
  "title": "Bosques del futuro",
  "description": "Proyecto de reforestación",
  "goalAmount": 25000,
  "durationDays": 45,
  "category": "Medio ambiente",
  "mediaUrl": "https://cdn.example.com/forest.jpg",
  "rewards": [
    {
      "title": "Agradecimiento",
      "description": "Mención virtual",
      "minimumAmount": 10
    },
    {
      "title": "Kit de apoyo",
      "description": "Recompensa física",
      "minimumAmount": 50
    }
  ]
}
```

Respuesta `201`:

```json
{
  "id": "UUID",
  "title": "Bosques del futuro",
  "description": "Proyecto de reforestación",
  "goalAmount": 25000,
  "durationDays": 45,
  "category": "Medio ambiente",
  "mediaUrl": "https://cdn.example.com/forest.jpg",
  "rewards": [
    {
      "id": "UUID",
      "title": "Agradecimiento",
      "description": "Mención virtual",
      "minimumAmount": 10,
      "quantity": null,
      "claimedQuantity": null
    }
  ],
  "completionPercentage": 50,
  "savedAt": "2026-01-01T00:00:00Z",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

Si faltan campos obligatorios como `goalAmount`, `durationDays` o `category`, responde `400` con el error de validación del primer campo.

### 2) Listar borradores del creador

`GET /api/drafts?page=0&pageSize=10`

Requiere sesión del creador.

Parámetros opcionales:
- `page` (default `0`)
- `pageSize` (default `10`)

Respuesta `200`:

```json
{
  "content": [
    {
      "id": "UUID",
      "title": "Bosques del futuro",
      "description": "Proyecto de reforestación",
      "goalAmount": 25000,
      "durationDays": 45,
      "category": "Medio ambiente",
      "mediaUrl": "https://cdn.example.com/forest.jpg",
      "rewards": [],
      "completionPercentage": 50,
      "savedAt": "2026-01-01T00:00:00Z",
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-01T00:00:00Z"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### 3) Obtener borrador por id

`GET /api/drafts/{draftId}`

Requiere sesión del creador que lo generó.

Respuesta `200`: el mismo objeto `DraftResponse` que en la creación.

### 4) Actualizar borrador

`PUT /api/drafts/{draftId}`

Requiere sesión del creador propietario.

Body (puede ser parcial):

```json
{
  "title": "Bosques del futuro v2",
  "goalAmount": 30000
}
```

Respuesta `200`: borrador actualizado con los datos nuevos.

### 5) Eliminar borrador

`DELETE /api/drafts/{draftId}`

Requiere sesión del creador propietario.

Respuesta `204 No Content`.

### 6) Publicar borrador

`POST /api/drafts/{draftId}/publish`

Requiere sesión del creador propietario.

Respuesta `201`: crea la campaña activa y devuelve el `CampaignDetailResponse`.

Ejemplo de respuesta:

```json
{
  "id": "UUID",
  "creator": {
    "id": "UUID",
    "name": "Ana",
    "email": "ana@example.com",
    "role": "creator",
    "verificationStatus": "verified"
  },
  "title": "Energía verde",
  "description": "Campaña de paneles solares",
  "goalAmount": 30000,
  "deadline": "2026-02-15T00:00:00Z",
  "category": "Medio ambiente",
  "mediaUrl": "https://example.com/solar.jpg",
  "status": "ACTIVE",
  "publishedAt": "2026-01-01T00:00:00Z",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z",
  "rewards": [
    {
      "id": "UUID",
      "title": "Gracias",
      "description": null,
      "minimumAmount": 10,
      "quantity": null,
      "claimedQuantity": null
    }
  ],
  "updates": [],
  "progress": {
    "raisedAmount": 13500,
    "remainingAmount": 16500,
    "percentage": 45,
    "sponsorsCount": 1,
    "secondsRemaining": 1234567
  }
}
```

### 7) Listar campañas públicas

`GET /api/campaigns?page=0&pageSize=10&category=Medio ambiente&status=ACTIVE`

Parámetros opcionales:
- `page` default `0`
- `pageSize` default `10`
- `category` filtro por categoría
- `status` filtro por estado de campaña

Respuesta `200`:

```json
{
  "content": [
    {
      "id": "UUID",
      "title": "Bosques de la vida",
      "description": "Proyecto para restaurar áreas verdes",
      "goalAmount": 30000,
      "deadline": "2026-02-20T00:00:00Z",
      "category": "Medio ambiente",
      "mediaUrl": "https://example.com/media/bosques.jpg",
      "status": "ACTIVE",
      "publishedAt": "2026-01-01T00:00:00Z",
      "progress": {
        "raisedAmount": 13500,
        "remainingAmount": 16500,
        "percentage": 45,
        "sponsorsCount": 1,
        "secondsRemaining": 1234567
      }
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### 8) Obtener campaña por id

`GET /api/campaigns/{campaignId}`

Respuesta pública `200` con detalle completo de la campaña.

### 9) Crear actualización de campaña

`POST /api/campaigns/{campaignId}/updates`

Requiere sesión autenticada del creador que publicó la campaña.

Body:

```json
{
  "title": "Avance del proyecto",
  "body": "Ya realizamos la primera fase de plantación y seguimos con la segunda etapa."
}
```

Respuesta `201`:

```json
{
  "id": "UUID",
  "campaignId": "UUID",
  "authorId": "UUID",
  "title": "Avance del proyecto",
  "body": "Ya realizamos la primera fase de plantación y seguimos con la segunda etapa.",
  "publishedAt": "2026-01-01T00:00:00Z"
}
```

## Datos de muestra generados por el seed

El seeder está desactivado por defecto y se activa solo en desarrollo con:

```powershell
$env:APP_SEED_ENABLED="true"
$env:SEED_CREATOR_PASSWORD="una-contrasena-de-desarrollo"
$env:SEED_SPONSOR_PASSWORD="otra-contrasena-de-desarrollo"
.\mvnw.cmd spring-boot:run
```

Cuando se habilita, crea lo siguiente:

- Usuario `example.creator@impulsafund.test`
  - rol: `creator`
  - estado: `verified`
  - activo: `true`
- Usuario `example.sponsor@impulsafund.test`
  - rol: `sponsor`
  - estado: `verified`
  - activo: `true`
- Cuenta social ficticia para Google:
  - `provider = google`
  - `providerUserId = seed-google-user-0001`
- Campaña de ejemplo:
  - `Bosques de la vida`
  - categoría: `Medio ambiente`
  - monto meta: `30000.00`
  - estado: `ACTIVE`
  - recompensas:
    - `Agradecimiento` mínimo `10.00`
    - `Kit verde` mínimo `50.00`
    - `Membresía especial` mínimo `150.00`
- Contribuciones de ejemplo:
  - `13500.00` confirmada
  - `2500.00` pendiente
  - `1200.00` fallida
- Sesiones de ejemplo:
  - una sesión viva para el sponsor
  - una sesión revocada para el creator

Los tokens generados por el seed no se imprimen ni se documentan en el código ni en este documento.

## Migración, seed y pruebas

Desde `plataforma_crowdfunding_backend`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

La migración `V1__create_auth_tables.sql` y `V2__campaign_tables.sql` se ejecutan al arrancar. Para PostgreSQL se configura `SUPABASE_DB_URL` (o una URL JDBC equivalente); para producción usar `AUTH_COOKIE_SECURE=true` y HTTPS. Durante la validación del proyecto se confirmó que la suite ejecuta correctamente con `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.
