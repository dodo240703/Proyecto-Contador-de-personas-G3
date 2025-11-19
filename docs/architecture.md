# Arquitectura y flujo de datos

Este proyecto implementa un flujo de ingesta y consulta para conteo de personas en aulas usando LoRaWAN/ChirpStack y microservicios Spring Boot.

## Componentes

- Eureka Server (`8761`): Registro y descubrimiento de servicios.
- Ingest Service (`8081`): Expone webhooks para recibir uplinks desde ChirpStack/LoRaWAN, decodifica payloads y persiste eventos.
- Dashboard API (`8082`): Expone endpoints de lectura para ocupación actual y series temporales.
- PostgreSQL (`5432`): Base de datos relacional para la entidad `Event`.
- (Opcional) Grafana (`3000`): Visualización.

## Flujo

1. Dispositivo LoRaWAN envía uplink.
2. ChirpStack reenvía al webhook configurado → `ingest-service` (`/ingest/...`).
3. `ingest-service` decodifica payloads:
   - RAW HEX o Base64 según el endpoint.
   - Interpreta bytes `in/out` o `[salieron, entraron]` para `count`.
   - Ajusta timestamps a zona horaria (America/Lima) cuando aplica.
4. Se persiste un `Event` en Postgres.
5. `dashboard-api` consulta los `Event`s:
   - `GET /api/aulas/{id}/ocupacion-actual`
   - `GET /api/aulas/{id}/series?from=...&to=...`
6. Frontends o dashboards consumen `dashboard-api` (o la BD) para visualización.

## Entidad principal: Event

- id, aulaId, nodoId
- ev: `in` | `out` | `count` | `unk` | `error`
- ts: `Instant` (almacenado con zona)
- rssi, snr (métricas de radio)
- personasEntraron, personasSalieron (solo para eventos `count`)

## Descubrimiento y configuración

- Todos los servicios cliente registran/consumen Eureka en `http://localhost:8761/eureka/` (ajustable por env `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`).
- Datasource configurable por variables de entorno estándar `SPRING_DATASOURCE_*` (usado en `docker-compose`).

## Seguridad básica

- Algunos endpoints de ingesta requieren header `x-api-key`. Por defecto está embebida como literal en código (`secret123`).
- Recomendación: externalizarla a `application.properties` o variable de entorno y rotarla periódicamente.

## Despliegue con Docker

El `docker-compose.yml`:
- Define una red `iot-network`.
- Levanta Postgres, Eureka, ambos servicios y Grafana.
- Mapea puertos públicos y define healthcheck para Eureka.

Ver instrucciones en el `README.md`.
