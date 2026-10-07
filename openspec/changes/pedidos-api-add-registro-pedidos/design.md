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
- **Subtotal y total no se persisten:** se calculan al mapear al DTO, y el redondeo es
  `HALF_EVEN` a escala 2. Como cantidad y precio no cambian, no hay riesgo de inconsistencia.
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

### Validación
- Bean Validation sobre los records de request: `@NotEmpty @Size(max=100) @Valid` en `lineas`;
  `@NotBlank @Size(max=64)` en `sku`; `@NotNull @Min(1)` en `cantidad`; `@NotNull @Positive
  @Digits(integer=17, fraction=2)` en `precioUnitario`.
- El chequeo de SKU duplicado se hace en el service y lanza `SkuDuplicadoException`. Es una regla
  sobre el conjunto de líneas, no sobre un campo, y así queda una excepción de dominio con su propio
  `type`.

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
- [Un POST reintentado duplica el pedido] → Está documentado como fuera de alcance. Una opción
  futura es el header `Idempotency-Key`.
- [H2 en memoria pierde los datos al reiniciar] → Es aceptable en el entorno de práctica. Los tests
  no dependen de persistencia entre ejecuciones.
- [Cambiar los `type` de ProblemDetail rompe a los consumidores] → Se definen como constantes y
  tienen tests que los verifican.
