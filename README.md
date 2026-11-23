# EBP03 Crowdfunding Platform — Backend

## 🎯 Objetivo general

Desarrollar el backend de una plataforma web de crowdfunding que permita
publicar campañas de financiación, gestionar los aportes de los
patrocinadores y llevar el registro de las transacciones asociadas,
dando soporte a las tres partes interesadas del sistema: creadores de
proyectos, patrocinadores/inversionistas y administradores de la
plataforma.


## Alcance de este repositorio

Diseño de la solución, backend básico de la aplicación web e integración
y despliegue de la plataforma.

## Configuración de PostgreSQL en producción

Cuando `SUPABASE_DB_URL` apunta a PostgreSQL, configura también:

```env
DB_DRIVER_CLASS_NAME=org.postgresql.Driver
JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

Los valores por defecto usan H2 para desarrollo local y pruebas.

## Aportes recurrentes a creadores

El backend expone `POST /api/subscriptions`, `GET /api/subscriptions/me` y
`POST /api/subscriptions/{subscriptionId}/cancel`. Todas las operaciones requieren
la sesión `AUTH_SESSION`; el patrocinador se obtiene de esa sesión y nunca del
cuerpo de la solicitud.

El primer cobro usa el `PaymentProvider` existente. En el proveedor simulado,
`sim_success` aprueba el cobro y `sim_failure` simula fondos insuficientes; otras
referencias se rechazan. Solo se conserva la referencia de pago aceptada por el
proveedor, nunca datos de tarjeta o credenciales.

La tarea programada procesa suscripciones `ACTIVE` vencidas cada 60 segundos
(configurable con `app.subscription.renewal-fixed-delay-ms`). Cada renovación se
bloquea por suscripción y utiliza una clave determinista
`subscription:{id}:charge:{epochSeconds}`. Las contribuciones, pagos,
comprobantes, intentos y notificaciones se escriben en la misma transacción.
La creación bloquea la fila del creador para serializar altas concurrentes y
evitar más de una suscripción activa por pareja patrocinador-creador. Cada
ejecución procesa como máximo 20 renovaciones.
Los fallos quedan en `subscription_charge_attempts` y `payments`, sin crear una
contribución o comprobante exitoso; la suscripción sigue `ACTIVE` y se reintenta
un mes después. Los cobros exitosos generan una contribución `CONFIRMED`, un
comprobante `SUCCESSFUL` y una notificación al creador.

Las fechas mensuales se calculan en UTC con `ZonedDateTime.plusMonths(1)`. Por
ejemplo, una fecha de cobro el 31 se ajusta al último día válido del mes
siguiente. Las pruebas backend se ejecutan desde `plataforma_crowdfunding_backend`
con `mvn test`.

## Reembolsos de campañas fijas fallidas

`CampaignClosureService` procesa campañas activas vencidas cada minuto y
también puede ejecutarse desde el servicio de campañas. Si una campaña `FIXED`
queda bajo la meta, la misma transacción marca la campaña `FAILED` y solo
cambia aportes `HELD` a `REFUNDED`; actualiza el pago exitoso más reciente, el
comprobante existente y sus fechas, y persiste una notificación interna por
aporte. No se realiza una transferencia externa. Los cierres se serializan con
bloqueo pesimista de campaña y reejecutarlos no duplica notificaciones.

El historial y detalle existentes están disponibles también en
`/api/transactions/me` y `/api/transactions/{contributionId}`. El detalle
expone la descripción de retención únicamente mientras el aporte y comprobante
estén `HELD` y la campaña siga `ACTIVE`; tras el cierre muestra el estado
`REFUNDED`, conserva el código original del comprobante y expone la fecha.
