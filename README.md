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
