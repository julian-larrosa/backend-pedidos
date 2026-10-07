# Design

## Context

Hoy el repo tiene solo `BackendPedidosApplication` sobre Spring Boot 4.1 / Java 21, con
spring-boot-starter-webmvc, data-jpa, validation, H2 y springdoc ya declarados en `pom.xml`. No hay
paquetes de dominio, ni manejador de errores, ni specs. `application.properties` no desactiva
open-in-view, aunque la convención del proyecto lo exige. La motivación está en proposal.md (Why) y
el comportamiento, en `specs/pedidos/spec.md`.

## Goals / Non-Goals

**Goals:**
- Que la estructura del primer recurso (paquetes, DTOs, manejador de errores) sirva de patrón para
  los siguientes.
- Que los importes sean exactos: nada de `double`, siempre `BigDecimal` con escala 2.

**Non-Goals:**
- Migraciones de esquema versionadas (Flyway/Liquibase). En H2 alcanza con `ddl-auto`.
- Optimizar consultas o paginar.

## Endpoints

### `POST /api/v1/pedidos`

Request (`application/json`):
```json
{
  "lineas": [
    { "sku": "ABC-1", "cantidad": 2, "precioUnitario": 10.50 },
    { "sku": "XYZ-9", "cantidad": 1, "precioUnitario": 3.00 }
  ]
}
```

Response 201, header `Location: /api/v1/pedidos/3f1c...`:
```json
{
  "id": "3f1c9a2e-...",
  "lineas": [
    { "sku": "ABC-1", "cantidad": 2, "precioUnitario": 10.50, "subtotal": 21.00 },
    { "sku": "XYZ-9", "cantidad": 1, "precioUnitario": 3.00,  "subtotal": 3.00 }
  ],
  "total": 24.00
}
```

Response 400 (`application/problem+json`), validación:
```json
{
  "type": "urn:sagant:pedidos:validacion",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "Uno o más campos no son válidos",
  "instance": "/api/v1/pedidos",
  "errores": [ { "campo": "lineas[0].cantidad", "mensaje": "debe ser mayor o igual a 1" } ]
}
```

Response 400, SKU repetido: `type` `urn:sagant:pedidos:sku-duplicado`, más la extensión
`"sku": "ABC-1"`.

Response 400, cuerpo malformado: `type` `urn:sagant:pedidos:solicitud-malformada`.

### `GET /api/v1/pedidos/{id}`

Response 200: mismo cuerpo que la respuesta 201 del POST.
Response 404: `type` `urn:sagant:pedidos:pedido-no-encontrado`.
Response 400 (id no UUID): `type` `urn:sagant:pedidos:solicitud-malformada`.

## Decisions

### Estructura de paquetes
`com.sagant.pedidos.pedido.{controller,service,repository,model,dto}` y
`com.sagant.pedidos.error` para el `@RestControllerAdvice` global y el catálogo de `type`. Así se
respeta la convención "primero por funcionalidad, después por capa". El advice no pertenece a
ninguna funcionalidad, así que va en su propio paquete transversal del repo.

### Modelo de persistencia: `@Entity Pedido` + `@Embeddable LineaPedido` en `@ElementCollection`
- `Pedido`: `id UUID` generado por el servidor (`@GeneratedValue(strategy = UUID)`), y
  `lineas List<LineaPedido>` con `@ElementCollection` + `@OrderColumn` para conservar el orden.
- `LineaPedido`: `sku`, `cantidad`, `precioUnitario` (`BigDecimal`, `precision 19, scale 2`).
- **Subtotal y total no se persisten:** se calculan al mapear al DTO. Como cantidad y precio no
  cambian, no hay riesgo de inconsistencia.
- **Alternativa descartada: `@OneToMany` con entidad `LineaPedido`.** Una línea de pedido no tiene
  identidad propia ni se modifica sola en este alcance, así que esa opción solo agrega una entidad
  y un id más.
- **Alternativa descartada: id `Long` secuencial.** Sin autenticación, un id secuencial permite
  recorrer todos los pedidos.

### Service transaccional que devuelve records
`PedidoService` con `registrar(RegistrarPedidoRequest) -> PedidoDto` y `obtener(UUID) -> PedidoDto`,
ambos `@Transactional` (el de lectura, `readOnly`). Con open-in-view desactivado, la colección de
líneas se inicializa y se mapea al record dentro de la transacción. El controller nunca ve
entidades.

### Escala de los importes: se normaliza al mapear al DTO
El `POST` mapea la entidad recién guardada, que está en memoria con la escala que mandó el cliente
(por ejemplo, `3` tiene escala 0). El `GET`, en cambio, la lee de la base con escala 2. Para que
ambos respondan igual, un único método de mapeo entidad → `PedidoDto`, usado por `registrar` y por
`obtener`, normaliza los importes así:
- `precioUnitario.setScale(2, RoundingMode.UNNECESSARY)`. La validación ya garantiza a lo sumo 2
  decimales, así que nunca redondea; si lo hiciera, falla en vez de alterar el importe en silencio.
- El `subtotal` es el precio unitario normalizado multiplicado por la cantidad, y el `total` es la
  suma de los subtotales, ambos con `setScale(2, RoundingMode.UNNECESSARY)`. Multiplicar por un
  entero y sumar no agrega decimales.

**Alternativa descartada: normalizar en la entidad antes de persistir.** Arreglaría el `POST`, pero
deja la garantía de la respuesta dependiendo del estado de la entidad. Normalizar en el mapeo
cubre los dos endpoints en un solo lugar.

### Validación
- Bean Validation sobre los records de request: `@NotEmpty @Size(max=100)` en
  `List<@NotNull @Valid LineaPedidoRequest> lineas`. Anotar los elementos de la lista hace que una
  línea `null` falle con `NotNull` en `lineas[i]` y no llegue al service. Con `@Valid` solo sobre
  la lista, un `null` pasaba la validación y el chequeo de SKU repetido terminaba en 500.
- `@NotBlank @Size(max=64)` en `sku`; `@NotNull @Min(1)` en `cantidad` (`Integer`); `@NotNull
  @Positive @Digits(integer=17, fraction=2)` en `precioUnitario`.
- El chequeo de SKU duplicado se hace en el service y lanza `SkuDuplicadoException`. Es una regla
  sobre el conjunto de líneas, no sobre un campo, y así queda una excepción de dominio con su propio
  `type`.

### Cantidad con decimales: `solicitud-malformada`
Una `cantidad` como `2.5` se rechaza con `type` `solicitud-malformada`, no con `validacion`:
- **Coherencia:** es un valor que no corresponde al tipo declarado del campo (entero), igual que
  `cantidad: "dos"`, que la spec ya clasifica como `solicitud-malformada`. Con `validacion` habría
  dos `type` distintos para el mismo tipo de error.
- **Dónde falla:** el error aparece al deserializar el JSON, antes de que exista un objeto para
  Bean Validation. Clasificarlo como `validacion` obligaría a recibir `cantidad` como `BigDecimal`
  y validar que no tenga parte decimal, lo que complica el DTO.
- **Consecuencia:** `2.0` también se rechaza, porque solo se acepta un número JSON entero.

Comportamiento de Jackson: en Jackson 2, `DeserializationFeature.ACCEPT_FLOAT_AS_INT` está activa
por defecto y trunca `2.5` a `2` sin error. No está verificado cómo se comporta Jackson 3 con
Spring Boot 4. Primero se escribe el test del escenario "Cantidad con decimales". Si el test
muestra que el decimal se acepta, se desactiva `ACCEPT_FLOAT_AS_INT` en el `JsonMapper` de la
aplicación con la propiedad `spring.jackson.deserialization.*` que corresponda o, si no la hay, con
un customizer del builder. Así el error llega como `HttpMessageNotReadableException` y el advice
lo mapea a `solicitud-malformada`. La propiedad elegida se documenta en el PR.

### Manejo de errores centralizado
Un único `@RestControllerAdvice` que extiende `ResponseEntityExceptionHandler` y mapea:
- `MethodArgumentNotValidException` → `validacion`, con la extensión `errores`.
- `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` → `solicitud-malformada`.
- `SkuDuplicadoException` → `sku-duplicado`.
- `PedidoNoEncontradoException` → `pedido-no-encontrado`.

Los `type` son constantes en una sola clase para que sean estables y fáciles de testear. Se usan
URN y no URL para no comprometer un dominio que no existe.

### Configuración
Agregar `spring.jpa.open-in-view=false` en `application.properties`.

## Impacto en componentes transversales

| Componente | Impacto | Motivo |
|---|---|---|
| Autenticación | Ninguno | Fuera de alcance (ver proposal.md). |
| Logging | Ninguno | No se agregan logs propios ni se configura logging. |
| Métricas | Ninguno | No se agregan métricas propias. |
| Resiliencia | Ninguno | No hay llamadas a otros servicios. |

No se reimplementa ningún componente de plataforma ni se agrega una dependencia sobre uno.

## Risks / Trade-offs

- [Cualquiera puede leer un pedido conociendo su UUID] → El UUID aleatorio dificulta adivinarlo.
  Agregar autenticación es un change futuro que depende de plataforma.
- [El precio lo informa el cliente y puede ser arbitrario] → Es una decisión explícita de
  alcance. La validación contra catálogo queda para un change futuro con su contrato cruzado.
- [Obtener el precio del catálogo en el futuro implica quitar `precioUnitario` del request] → Eso
  es un cambio incompatible del contrato público, porque los clientes que hoy lo envían dejarían de
  cumplirlo. Requerirá una nueva versión de la API (por ejemplo, `/api/v2/pedidos`) y un plan de
  convivencia con `v1`. Esa decisión se toma en ese change, no en este.
- [Un POST reintentado duplica el pedido] → Está documentado como fuera de alcance. Una opción
  futura es el header `Idempotency-Key`.
- [H2 en memoria pierde los datos al reiniciar] → Es aceptable en el entorno de práctica. Los tests
  no dependen de persistencia entre ejecuciones.
- [Cambiar los `type` de ProblemDetail rompe a los consumidores] → Se definen como constantes y
  tienen tests que los verifican.
