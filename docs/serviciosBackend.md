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

### 5) Consultar y actualizar datos personales

La URL base local es `http://localhost:8080`. Estos endpoints requieren la cookie
`AUTH_SESSION` creada al iniciar sesión y siempre resuelven la cuenta desde esa
sesión; no aceptan identificadores de usuario.

`GET /api/auth/me` devuelve los valores actuales de la tabla `users`:

```json
{
  "id": "UUID",
  "name": "Ana Pérez",
  "email": "ana@example.com",
  "role": "creator",
  "verificationStatus": "verified"
}
```

`PATCH /api/auth/me` actualiza únicamente `name`.

Body:

```json
{
  "name": "Ana Pérez"
}
```

El nombre se recorta antes de guardar y debe contener entre 2 y 120 caracteres
de texto permitido. Campos adicionales y protegidos se rechazan.
Las modificaciones concurrentes se serializan con bloqueo de la fila del
usuario; prevalece la última transacción que obtenga y complete el bloqueo.

Respuesta `200`:

```json
{
  "message": "Datos actualizados",
  "user": {
    "id": "UUID",
    "name": "Ana Pérez",
    "email": "ana@example.com",
    "role": "creator",
    "verificationStatus": "verified"
  }
}
```

Los errores de validación responden `400` e identifican cada campo para mostrar
mensajes inline:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Revisa los campos indicados.",
  "details": [
    {
      "field": "name",
      "code": "REQUIRED",
      "message": "El nombre es obligatorio."
    }
  ]
}
```

Una cookie ausente, inválida, vencida o revocada responde `401 Unauthorized`.
El correo se mantiene de solo lectura porque el proyecto no cuenta con un flujo
de verificación para reemplazarlo de forma segura. No se necesita migración: la
tabla `users` existente ya tiene `name VARCHAR(120) NOT NULL`,
`email VARCHAR(320) NOT NULL UNIQUE` y `updated_at`.

Prueba manual: primero inicia sesión normalmente y conserva la cookie en el
mismo cliente HTTP. Luego solicita `GET http://localhost:8080/api/auth/me` para
precargar el formulario y envía `PATCH http://localhost:8080/api/auth/me` con
`Content-Type: application/json` y el body anterior. GET/PATCH sin cookie
devuelven `401`; un PATCH exitoso devuelve el nombre actualizado y conserva la
sesión.

### 6) Cierre de sesión

`POST /api/auth/logout`

No requiere sesión vigente para permitir reintentos idempotentes.

Qué hace:
- lee la cookie `AUTH_SESSION`
- revoca la sesión asociada
- elimina la cookie del cliente
- responde `204 No Content`

## Perfil de creador y enlaces

Base URL local: `http://localhost:8080`

`GET /api/creators/me/profile` y `PUT /api/creators/me/profile` requieren la
cookie `AUTH_SESSION` de una cuenta `CREATOR`. El propietario siempre se obtiene
de la sesión; no se aceptan identificadores del usuario o del perfil. Una
sesión ausente, vencida o revocada responde `401`. Un usuario autenticado que no
sea creador recibe `403 CREATOR_ROLE_REQUIRED`.

Si todavía no existe un perfil, el GET responde `200` con `id`, los datos
bancarios y las fechas en `null`, `links: []`, `userId` de la sesión y
`displayName` precargado desde `users.name`. El endpoint PUT crea el perfil o
actualiza el existente.

### Política regional y campos requeridos

El perfil aplica la política inicial de Colombia acordada para esta HU:

- `displayName` (2–120 caracteres), `bankAccountType`, `bankName`,
  `bankAccountNumber`, `bankAccountHolder` y `bankAccountDocument` son
  obligatorios.
- El tipo de cuenta admite `SAVINGS` o `CHECKING`.
- El teléfono es opcional; si se informa, debe ser un celular colombiano
  `+573` seguido de nueve dígitos.
- El número de cuenta solo admite 5–80 dígitos; el documento colombiano, 5–15
  dígitos. No se eliminan ni normalizan separadores: se rechazan.
- `bio` es opcional y admite hasta 2000 caracteres; `professionalTitle` es
  opcional y admite hasta 120 caracteres.
- `links` es una lista obligatoria (usa `[]` si no hay enlaces), de hasta 20
  elementos. Cada enlace admite `PORTFOLIO`, `PROFESSIONAL_NETWORK` u
  `OTHER_PROJECT`, URL absoluta `http`/`https` y una etiqueta opcional. HTTPS
  se recomienda. No se permiten URLs duplicadas dentro del perfil. La lista
  se reemplaza por completo en cada PUT: sus IDs se regeneran y su orden se
  normaliza a partir de `sortOrder` (cero en adelante).

No hay proveedor de desembolsos real ni política existente que exija
verificación para guardar los datos; por ello el servicio exige rol `CREATOR`,
pero no `verificationStatus = VERIFIED`. Los cinco campos bancarios sí son
requeridos por este contrato. El perfil no modifica el estado de verificación
ni crea sesiones, cuentas, campañas o aportes.

### Cifrado y privacidad bancaria

El número de cuenta y el documento se cifran con AES-256-GCM. Se requiere
configurar `CREATOR_PROFILE_ENCRYPTION_KEY` con una clave aleatoria de 32 bytes,
codificada en Base64, antes de iniciar el backend. No se genera una clave
temporal por defecto: sin clave válida la aplicación falla al iniciar, para no
guardar datos que luego no puedan descifrarse. Genérala una sola vez, almacénala
en el gestor de secretos del entorno y conserva una copia de respaldo protegida;
perder o rotar la clave sin migrar los datos impide descifrar los valores ya
guardados. No la incluyas en el repositorio ni en logs. La clave definida en
`application-test.properties` es solo para pruebas.

Las respuestas solo exponen `bankAccountNumberMasked` y
`bankAccountDocumentMasked`; nunca devuelven los valores completos. No se
reciben ni guardan CVV, PIN, contraseña bancaria, credenciales bancarias o
números completos de tarjeta.

### Consultar perfil

`GET /api/creators/me/profile`

Ejemplo de perfil existente:

```json
{
  "id": "UUID",
  "userId": "UUID",
  "displayName": "Ana Pérez",
  "bio": "Diseñadora y creadora de proyectos ambientales.",
  "professionalTitle": "Diseñadora social",
  "phone": "+573001112233",
  "bankAccountType": "SAVINGS",
  "bankName": "Banco de Prueba",
  "bankAccountNumberMasked": "******7890",
  "bankAccountHolder": "Ana Pérez",
  "bankAccountDocumentMasked": "*******89",
  "links": [
    {
      "id": "UUID",
      "linkType": "PORTFOLIO",
      "label": "Mi portafolio",
      "url": "https://portfolio.example/ana",
      "sortOrder": 0
    }
  ],
  "createdAt": "2026-10-16T03:00:00Z",
  "updatedAt": "2026-10-16T03:05:00Z"
}
```

### Crear o actualizar perfil

`PUT /api/creators/me/profile`

```json
{
  "displayName": "Ana Pérez",
  "bio": "Diseñadora y creadora de proyectos ambientales.",
  "professionalTitle": "Diseñadora social",
  "phone": "+573001112233",
  "bankAccountType": "SAVINGS",
  "bankName": "Banco de Prueba",
  "bankAccountNumber": "1234567890",
  "bankAccountHolder": "Ana Pérez",
  "bankAccountDocument": "123456789",
  "links": [
    {
      "linkType": "PORTFOLIO",
      "label": "Mi portafolio",
      "url": "https://portfolio.example/ana",
      "sortOrder": 0
    },
    {
      "linkType": "PROFESSIONAL_NETWORK",
      "label": "Red profesional",
      "url": "https://www.linkedin.com/in/ana",
      "sortOrder": 1
    }
  ]
}
```

Una respuesta `200` confirma la transacción con el mensaje exacto
`"Perfil guardado"` y contiene el perfil actualizado con valores bancarios
enmascarados. La operación guarda el perfil y reemplaza sus enlaces en la
misma transacción; cualquier error revierte ambos.

Los errores responden `400 VALIDATION_ERROR` con los campos para mostrar
mensajes inline:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Revisa los campos indicados.",
  "details": [
    {
      "field": "profile.bankAccountNumber",
      "code": "INVALID_FORMAT",
      "message": "El número de cuenta debe contener entre 5 y 80 dígitos."
    },
    {
      "field": "links[0].url",
      "code": "INVALID_URL",
      "message": "Ingresa una URL válida."
    }
  ]
}
```

Prueba manual: inicia sesión como creador y conserva su cookie `AUTH_SESSION`;
llama a `GET http://localhost:8080/api/creators/me/profile` para precargar el
formulario y envía el body a `PUT` en esa misma URL con
`Content-Type: application/json`. GET/PUT sin sesión responden `401`; un
patrocinador recibe `403`; una cuenta inválida devuelve errores de campo y no
guarda cambios.

## Restablecimiento de contraseña

El flujo es público y no requiere `AUTH_SESSION`. La respuesta de solicitud es
siempre `202 Accepted` con el mismo cuerpo para correos registrados, inexistentes,
inválidos o temporalmente bloqueados. El backend no recibe `userId`: la cuenta se
resuelve por el correo normalizado y el token se entrega por el adaptador
`PasswordResetNotificationSender`.

### 1) Solicitar instrucciones

`POST /api/auth/password-reset/request`

```json
{
  "email": " usuario@example.com "
}
```

Respuesta pública:

```json
{
  "message": "Si la cuenta cumple los requisitos, recibirás instrucciones para restablecer tu contraseña."
}
```

Los tokens se generan con alta entropía, se guardan únicamente como SHA-256,
expiran por defecto en 15 minutos y los tokens anteriores no usados se
invalidan. Nunca aparecen en respuestas, logs ni auditoría. Se aplica un límite
por IP (`PASSWORD_RESET_IP_LIMIT`, por defecto 5 solicitudes por ventana) y un
límite de tres fallos por cuenta/IP; ambos usan la misma respuesta genérica.

### 2) Validar token

`POST /api/auth/password-reset/validate`

```json
{
  "token": "valor-recibido-por-el-canal-configurado"
}
```

Respuesta `200`:

```json
{
  "valid": true,
  "expiresAt": "2026-10-16T03:12:00Z"
}
```

Un token inexistente, usado, expirado o bloqueado devuelve `400 RESET_TOKEN_INVALID`.

### 3) Confirmar nueva contraseña

`POST /api/auth/password-reset/confirm`

```json
{
  "token": "valor-recibido-por-el-canal-configurado",
  "newPassword": "NuevaSegura1!"
}
```

Respuesta `200`:

```json
{
  "message": "Contraseña actualizada correctamente.",
  "loginRequired": true
}
```

La contraseña exige mínimo ocho caracteres, mayúscula, minúscula, número y
símbolo, y no puede ser igual a la actual. Una confirmación válida actualiza el
hash BCrypt, marca el token como usado, revoca todas las sesiones, registra
`PASSWORD_CHANGED` y crea una notificación interna `PASSWORD_CHANGED` en una
sola transacción. Los errores de complejidad usan `VALIDATION_ERROR`/`WEAK_PASSWORD`
con detalles del campo; nunca incluyen hashes, tokens ni información de la cuenta.

En desarrollo/pruebas debe registrarse un bean `PasswordResetNotificationSender`
(el perfil de pruebas incluye un adaptador falso). En producción se debe
configurar `PASSWORD_RESET_NOTIFICATION_ENABLED=true` y
`PASSWORD_RESET_NOTIFICATION_ENDPOINT` con un adaptador HTTP de entrega de
correo real, sin cambiar estos contratos públicos. El adaptador
envía el token únicamente al proveedor configurado; nunca lo escribe en logs.
La expiración y los límites se configuran mediante:
`PASSWORD_RESET_TOKEN_MINUTES`, `PASSWORD_RESET_WINDOW_MINUTES` y
`PASSWORD_RESET_IP_LIMIT`.

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

Requiere la cookie `AUTH_SESSION` de una cuenta activa con rol `CREATOR`, propietaria de la campaña. Se admiten campañas `ACTIVE` no vencidas y `PUBLISHED`; campañas inexistentes, no propias o cerradas se rechazan.

Body:

```json
{
  "title": "Avance del proyecto",
  "body": "Ya realizamos la primera fase de plantación y seguimos con la segunda etapa.",
  "media": [
    {
      "mediaType": "IMAGE",
      "url": "https://cdn.example/avance.jpg",
      "sortOrder": 0
    }
  ]
}
```

`title` se recorta, es obligatorio y admite hasta 140 caracteres. `body` se almacena como texto plano (no se interpreta como HTML), es obligatorio y su longitud máxima se configura con `app.campaign-updates.max-body-length` (por defecto 10000, máximo configurable 50000). Se rechazan contenido HTML y esquemas peligrosos. Se permiten hasta 10 medios `IMAGE` o `VIDEO`, con URL absoluta HTTPS; el servidor no descarga ni consulta las URLs. Los campos de autoría, campaña y publicación se determinan en el servidor y los campos JSON desconocidos se rechazan.

La actualización, sus medios y sus notificaciones se persisten en una transacción. Se notifica una sola vez a cada usuario con aporte `CONFIRMED` en la campaña, excluyendo al creador. Aportes `PENDING`, `FAILED` y `REFUNDED` no generan notificaciones. No se usa `Idempotency-Key` para este endpoint.

Respuesta `201` incluye el contenido persistido y `notificationsCreated`:

```json
{
  "id": "UUID",
  "campaignId": "UUID",
  "authorId": "UUID",
  "title": "Avance del proyecto",
  "body": "Ya realizamos la primera fase de plantación y seguimos con la segunda etapa.",
  "publishedAt": "2026-01-01T00:00:00Z",
  "media": [
    {
      "id": "UUID",
      "mediaType": "IMAGE",
      "url": "https://cdn.example/avance.jpg",
      "sortOrder": 0
    }
  ],
  "notificationsCreated": 3
}
```

### 10) Consultar muro de campaña

`GET /api/campaigns/{campaignId}/updates?page=0&pageSize=10`

El muro es público para campañas visibles (no `DRAFT`). La respuesta contiene solo actualizaciones de esa campaña, ordenadas por `publishedAt DESC, id DESC`. `page` debe ser no negativo y `pageSize` debe estar entre 1 y 50. La respuesta paginada incluye `content`, `page`, `pageSize`, `totalElements` y `totalPages`; cada actualización incluye sus medios ordenados.

### 11) Consultar notificaciones propias

`GET /api/notifications/me?page=0&pageSize=20&unreadOnly=false`

Requiere sesión autenticada. El servidor filtra exclusivamente por el usuario de `AUTH_SESSION`, ordena por fecha descendente y permite solicitar solo las no leídas. No se acepta un destinatario proporcionado por el cliente.

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
  - `75.00 USD` confirmada, con recompensa `Kit verde` y pago `SUCCEEDED`
  - `25.00 USD` fallida, con `failureCode = SIMULATED_FAILURE`; no impacta progreso
- Evento `seed-webhook-success` procesado para el pago confirmado. Reenviarlo no crea otro evento por la restricción única `provider_event_id`.
- Simulación disponible con `paymentMethodId = sim_success` y `paymentMethodId = sim_failure`.
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

Las migraciones Flyway se ejecutan al arrancar. Para PostgreSQL se configura `SUPABASE_DB_URL` (o una URL JDBC equivalente); para producción usar `AUTH_COOKIE_SECURE=true`, HTTPS, `PAYMENT_PLATFORM_FEE_RATE` y un `PAYMENT_WEBHOOK_SECRET` real. Ejecutar las pruebas específicas de publicación de actualizaciones con `.\mvnw.cmd "-Dtest=CampaignUpdatePublicationIntegrationTests" test` y la suite de regresión con `.\mvnw.cmd test`.

## Aportes, pagos y progreso

Los aportes requieren sesión `AUTH_SESSION` de un usuario con rol `sponsor`. El backend nunca recibe ni guarda número de tarjeta, CVV o vencimiento: `paymentMethodId` es un identificador opaco que se envía al proveedor configurado. En desarrollo, el proveedor simulado acepta `sim_success`/`success` o `sim_failure`/`failure`.

### 1) Crear un aporte

`POST /api/campaigns/{campaignId}/contributions`

Headers:

```text
Cookie: AUTH_SESSION=<sesion-del-sponsor>
Idempotency-Key: aporte-2026-0001
Content-Type: application/json
```

Body:

```json
{
  "amount": 75.00,
  "currency": "USD",
  "rewardId": "UUID-de-Kit-verde",
  "paymentMethodId": "sim_success"
}
```

El servidor verifica campaña activa y no vencida, monto positivo, moneda de tres letras, pertenencia de la recompensa, mínimo, inventario y rol. Todas las contribuciones de una campaña deben usar la misma moneda: la primera contribución registrada establece la moneda y una solicitud posterior con otra moneda responde `409 CAMPAIGN_CURRENCY_MISMATCH`. La misma clave para el mismo sponsor devuelve el mismo aporte y pago. Un fallo responde con el estado `FAILED`, mensaje genérico y permite reintento; el código técnico queda solo en `payments.failure_code`.

Respuesta `201`:

```json
{
  "contribution": {
    "id": "UUID",
    "campaignId": "UUID",
    "sponsorId": "UUID",
    "rewardId": "UUID",
    "amount": 75.00,
    "currency": "USD",
    "status": "CONFIRMED"
  },
  "id": "UUID",
  "campaignId": "UUID",
  "sponsorId": "UUID",
  "rewardId": "UUID",
  "amount": 75.00,
  "currency": "USD",
  "status": "CONFIRMED",
  "payment": { "id": "UUID", "status": "SUCCEEDED" },
  "createdAt": "2026-09-20T12:00:00Z"
}
```

### 2) Aportes propios

`GET /api/contributions/me?page=0&pageSize=10&status=CONFIRMED`

Requiere sesión `AUTH_SESSION` de un usuario con rol `SPONSOR`. El sponsor se obtiene de la sesión y nunca de parámetros. `status` es opcional (`PENDING`, `CONFIRMED`, `FAILED`, `REFUNDED`); `page` debe ser no negativo y `pageSize` está limitado de 1 a 50. El historial se pagina en base de datos y se ordena por fecha e identificador descendentes. Un filtro o valor de paginación inválido responde `400 INVALID_PARAMETER`; un creator recibe `403`.

La respuesta resume cada aporte con fecha, campaña, monto decimal, moneda, referencia y estado de comprobante y de pago. No incluye `idempotency_key`, secretos ni información técnica del proveedor. Los comprobantes se guardan en `transaction_receipts`; V8 genera una referencia para aportes históricos confirmados con pago exitoso. En nuevas transacciones, el backend emite comprobante al confirmar el pago. Un aporte pendiente o fallido no recibe comprobante. Los estados de comprobante son `HELD` (Retenido), `SUCCESSFUL` (Exitoso), `REFUNDED` (Reembolsado) y `FAILED` (Fallido).

Sin aportes, se devuelve `content: []`, cero elementos/páginas y `emptyMessage: "Aún no has realizado aportes"`.

```json
{
  "content": [
    {
      "contributionId": "UUID",
      "date": "2026-10-16T03:10:00Z",
      "campaignId": "UUID",
      "projectName": "Bosques de la vida",
      "amount": 100.00,
      "currency": "USD",
      "referenceCode": "REF-UUID",
      "receiptStatus": "SUCCESSFUL",
      "receiptStatusLabel": "Exitoso",
      "contributionStatus": "CONFIRMED",
      "paymentStatus": "SUCCEEDED"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### 3) Detalle de aporte

`GET /api/contributions/{contributionId}`

Lo puede consultar el sponsor propietario o el creator propietario de la campaña. Devuelve el mismo detalle seguro del aporte, sin método de pago, tarjeta, CVV, tokens ni mensajes del proveedor.

### 4) Reintentar pago

`POST /api/contributions/{contributionId}/retry`

Headers: `Idempotency-Key` nuevo y cookie del sponsor propietario.

Body:

```json
{ "paymentMethodId": "sim_success" }
```

El reintento crea un nuevo registro `payments` asociado al mismo aporte. Si el aporte ya está confirmado, devuelve el pago confirmado y no crea un cobro adicional.

### 5) Webhook del proveedor

`POST /api/payments/webhook?paymentId={paymentId}&eventType=payment.succeeded&status=succeeded|failed|refunded|processing`

Headers: `X-Provider-Event-Id` único y `X-Payment-Signature`. La firma es HMAC-SHA256 con clave `PAYMENT_WEBHOOK_SECRET` sobre la concatenación de los campos `payload`, `eventId`, `paymentId`, `eventType` y `status`; cada campo se codifica como `longitud UTF-8:valor` y el resultado se envía en hexadecimal. El cuerpo es el payload crudo del proveedor. Un adaptador transforma el estado externo al estado interno sin exponer el payload ni los secretos. Se valida la firma, se guarda `providerEventId` y se procesa el cambio una sola vez. Los cambios de éxito/refund actualizan `payment`, `contribution` y el comprobante en la misma transacción; eventos duplicados o tardíos no revierten un reembolso. Firma inválida responde `401` y no persiste el evento.

### 6) Campañas apoyadas

`GET /api/sponsors/me/supported-campaigns?page=0&pageSize=10`

Requiere sesión `AUTH_SESSION` de un usuario con rol `SPONSOR`. El patrocinador se obtiene exclusivamente de la sesión. Solo se aceptan `page` y `pageSize`; enviar otros parámetros (incluidos `sponsorId`, `userId` o identificadores de campañas) responde `400 INVALID_PARAMETER`. `page` debe ser no negativo y `pageSize` debe estar entre 1 y 50. Un creator recibe `403` y una solicitud sin sesión recibe `401`.

El tablero contiene una tarjeta por campaña con al menos un aporte `CONFIRMED` del patrocinador. `amountContributedByMe` suma sus aportes confirmados; `raisedAmount` suma los aportes confirmados de todos los patrocinadores. Los aportes `PENDING`, `FAILED` y `REFUNDED` se excluyen. La moneda debe ser única para toda la campaña; campañas históricas que tengan aportes confirmados en monedas distintas se omiten del tablero y se registra una advertencia en el backend. Los montos se agregan en la base de datos y el porcentaje se calcula con precisión decimal, redondeado a dos decimales y limitado a 100.

Las tarjetas se ordenan por la fecha del último aporte confirmado del patrocinador, descendente, y `campaignId` descendente como desempate. Los códigos internos se presentan así: `ACTIVE`/`PUBLISHED` → `ACTIVA`, `SUCCESSFUL` → `EXITOSA`, `FAILED` → `FALLIDA`, `DRAFT` → `BORRADOR` y `CANCELLED` → `CANCELADA`.

Respuesta `200`:

```json
{
  "content": [
    {
      "campaignId": "UUID",
      "imageUrl": "https://cdn.example/campaign.jpg",
      "title": "Bosques de la vida",
      "amountContributedByMe": 125.00,
      "currency": "USD",
      "raisedAmount": 5000.00,
      "goalAmount": 10000.00,
      "totalRaisedPercentage": 50.00,
      "status": "ACTIVA",
      "statusCode": "ACTIVE",
      "lastContributionAt": "2026-10-16T03:10:00Z"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Sin campañas apoyadas, `content` es `[]`, `totalElements` y `totalPages` son cero, y `emptyMessage` es `"Aún no has apoyado ningún proyecto"`.

### 7) Progreso público

`GET /api/campaigns/{campaignId}/progress`

No requiere sesión para campañas públicas. Respuesta:

```json
{
  "goalAmount": 30000.00,
  "raisedAmount": 75.00,
  "remainingAmount": 29925.00,
  "percentage": 0,
  "sponsorsCount": 1,
  "secondsRemaining": 1234567,
  "status": "ACTIVE"
}
```

Solo los aportes `CONFIRMED` incrementan `raisedAmount`, porcentaje, sponsors e inventario reclamado. La comisión se calcula con `PAYMENT_PLATFORM_FEE_RATE` (por defecto `0.05`) y no se fija en el frontend.

### Errores y reglas de seguridad

- Sin sesión: `401`; creator intentando aportar o sponsor accediendo a otro aporte: `403`.
- Monto cero/negativo, moneda inválida, reward de otra campaña o mínimo incumplido: `400`.
- Campaña inactiva o vencida: `409` con código `CAMPAIGN_NOT_ACTIVE`.
- Moneda incompatible con la ya usada en la campaña: `409 CAMPAIGN_CURRENCY_MISMATCH`.
- No se aceptan tarjetas, CVV ni fechas de vencimiento en ningún body.
- Una intención `PENDING` o una respuesta de pago no definitiva no cambia el progreso.
