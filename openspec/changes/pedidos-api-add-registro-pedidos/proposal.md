# Proposal

## Change ID

`pedidos-api-add-registro-pedidos`

## Why

pedidos-api todavía no expone ninguna funcionalidad de dominio: solo existe el arranque de la
aplicación. Se necesita poder registrar un pedido con una o más líneas de pedido y consultarlo
después, que es la base sobre la que se construirá el resto del servicio.

## What Changes

- Nuevo endpoint `POST /api/v1/pedidos` para registrar un pedido con entre 1 y 100 líneas de
  pedido. Cada línea de pedido tiene SKU, cantidad y precio unitario, enviados por el cliente.
- Nuevo endpoint `GET /api/v1/pedidos/{id}` para consultar un pedido registrado por su
  identificador (UUID).
- El servidor calcula el subtotal de cada línea de pedido y el total del pedido; el cliente no
  los envía.
- Un pedido con el mismo SKU en más de una línea de pedido se rechaza con un error específico.
- Los errores se devuelven como ProblemDetail (RFC 9457) con un `type` estable por caso. Este
  change crea el manejador centralizado de errores del servicio.
- Se desactiva open-in-view de JPA (`spring.jpa.open-in-view=false`), alineando la
  configuración con la convención declarada del proyecto.

### Decisiones confirmadas

- El precio unitario es un `BigDecimal` mayor que cero con hasta 2 decimales.
- El SKU es un texto no vacío de hasta 64 caracteres; no se verifica que exista en un catálogo.
- Un pedido tiene entre 1 y 100 líneas de pedido.
- Los `type` de ProblemDetail son URN con el formato `urn:sagant:pedidos:<caso>`.
- Una única moneda implícita.
- La cantidad es un entero mayor o igual a 1.
- La comparación de SKU distingue mayúsculas y minúsculas.
- La persistencia es la base H2 del entorno de práctica; los pedidos no sobreviven a un
  reinicio.

### Fuera de alcance

- Listar, buscar o paginar pedidos.
- Estados del pedido y sus transiciones; modificar o cancelar un pedido.
- Validar SKU o precio contra un catálogo, u obtener el precio desde él.
- Dueño del pedido, autenticación y autorización: cualquiera que conozca el id puede
  consultarlo.
- Idempotencia de `POST`: un reintento del cliente registra un pedido nuevo.
- Monedas múltiples, impuestos y descuentos.
- Logging o métricas de negocio específicos.

## Capabilities

### New Capabilities

- `pedidos`: registro y consulta de pedidos con sus líneas de pedido a través de la API
  `/api/v1/pedidos`.

### Modified Capabilities

Ninguna.

## Contratos afectados

- **Nuevo contrato público:** `POST /api/v1/pedidos` y `GET /api/v1/pedidos/{id}`, incluidos los
  `type` de ProblemDetail. Desde que se publica, cambiarlo es un cambio de contrato.
- **Contratos cruzados existentes modificados:** ninguno. No se consume ningún otro servicio
  (no hay integración con catálogo).
- **Consumidores conocidos:** ninguno por ahora. Cuando exista un consumidor, el contrato se
  publicará en repo-specs según la sección 3.2 de la guía de la empresa.

## Componentes transversales involucrados

- **Autenticación:** no se requiere; queda fuera de alcance de forma explícita. Si se
  restringiera la consulta al dueño del pedido, haría falta la librería de autenticación de
  plataforma, que todavía no existe, y habría que proponerla primero en el repo de plataforma.
- **Logging y métricas:** no se agregan logs ni métricas propios. No hay dependencia nueva de
  plataforma.
- **Resiliencia:** no aplica, porque no hay llamadas a otros servicios.
- El manejo de errores con ProblemDetail es una convención interna del repo, no un componente
  de plataforma.

## Estrategia de testing

- Cada Scenario de `specs/pedidos/spec.md` tiene al menos un test automatizado que lo nombra.
- Tests de integración de la capa web con `@SpringBootTest` + MockMvc contra H2, que verifican el
  código HTTP, el cuerpo de la respuesta, el header `Location` y el `type` del ProblemDetail.
- Tests unitarios del cálculo de subtotales y total con `BigDecimal`.
- `./mvnw test` en verde como condición para completar cada grupo de tareas y para archivar.

## Aprobadores requeridos

- Julián Larrosa, tech lead del repositorio: aprobación de la proposal antes de implementar y
  del PR.
- No se requieren otros aprobadores: no hay repos consumidores ni cambios de UI, y no se usan
  ni proponen componentes transversales.

## Impact

- **Código nuevo:** paquete `com.sagant.pedidos.pedido` (controller, service, repository,
  model, dto) y un `@RestControllerAdvice` global.
- **Configuración:** `src/main/resources/application.properties` (open-in-view).
- **Base de datos:** tablas nuevas para pedidos y líneas de pedido en H2, generadas por JPA.
- **Dependencias:** ninguna nueva; alcanza con las que ya están en `pom.xml` (web, JPA,
  validation, H2, springdoc).
