# Dashboard API

Base URL por defecto: `http://localhost:8082/api`

Lee eventos de la tabla `events` y entrega ocupación actual y series de deltas por aula.

## Endpoints

### GET /aulas/{id}/ocupacion-actual
Devuelve la ocupación instantánea calculada como suma de deltas (`in`=+1, `out`=−1) hasta el momento.

Path params:
- `id` — identificador del aula (por ejemplo `A101`)

Respuesta (JSON — OcupacionDTO):
```json
{ "aula": "A101", "ocupacion": 12 }
```

---

### GET /aulas/{id}/series?from=...&to=...
Devuelve los eventos como una lista de puntos `{ts, delta}` ordenados por tiempo. Útil para reconstruir series de ocupación.

Path params:
- `id` — identificador del aula

Query params:
- `from` — ISO 8601, p. ej. `2025-09-10T10:00:00Z`
- `to` — ISO 8601, p. ej. `2025-09-10T12:00:00Z`

Respuesta (JSON — `SeriesPoint[]`):
```json
[
  { "ts": "2025-09-10T10:00:00Z", "delta": 1 },
  { "ts": "2025-09-10T10:05:00Z", "delta": -1 }
]
```

## Notas

- El repositorio JPA ejecuta:
  - `ocupacionActual(aula)` → `sum(case when ev='in' then 1 else -1 end)`
  - `eventosEnRango(aula, from, to)` → eventos ordenados por `ts`.
- Se asume que la ingesta produce `ev` con valores `in`/`out` (y opcionalmente `count` para otras vistas).

## Ejemplos de cURL

```bash
curl "http://localhost:8082/api/aulas/A101/ocupacion-actual"

curl "http://localhost:8082/api/aulas/A101/series?from=2025-09-10T10:00:00Z&to=2025-09-10T12:00:00Z"
```
