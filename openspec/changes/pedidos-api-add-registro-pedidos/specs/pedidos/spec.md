# Spec Delta

## Purpose

Permite registrar pedidos compuestos por una o más líneas de pedido (SKU, cantidad y precio
unitario) y consultarlos después por su identificador a través de la API `/api/v1/pedidos`.

## Glosario

- **Pedido:** conjunto de una o más líneas de pedido registrado en una única solicitud,
  identificado por un UUID asignado por el servidor.
- **Línea de pedido:** combinación de un SKU, una cantidad y un precio unitario dentro de un
  pedido.
- **Precio unitario:** precio de una unidad del SKU, enviado por el cliente. Es un decimal mayor
  que cero con a lo sumo 2 decimales.
- **Subtotal:** precio unitario multiplicado por la cantidad de una línea de pedido, calculado
  por el servidor.
- **Total:** suma de los subtotales de todas las líneas de pedido de un pedido, calculada por el
  servidor.

## ADDED Requirements

### Requirement: Registrar un pedido
El sistema SHALL registrar un pedido cuando recibe `POST /api/v1/pedidos` con un cuerpo JSON que
contiene un arreglo `lineas` con entre 1 y 100 líneas de pedido válidas. La respuesta MUST tener
código 201, el header `Location` con la ruta `/api/v1/pedidos/{id}` del pedido creado y, en el
cuerpo, el pedido con su `id`, sus líneas de pedido, el subtotal de cada una y el total. Todos los
importes de las respuestas (`precioUnitario`, `subtotal`, `total`) MUST tener exactamente 2
decimales.

#### Scenario: Registro exitoso de un pedido con dos líneas de pedido
- **WHEN** el cliente envía `POST /api/v1/pedidos` con las líneas de pedido `{sku: "ABC-1", cantidad: 2, precioUnitario: 10.50}` y `{sku: "XYZ-9", cantidad: 1, precioUnitario: 3.00}`
- **THEN** el sistema responde 201 con el header `Location` igual a `/api/v1/pedidos/{id}`, donde `{id}` es un UUID
- **AND** el cuerpo contiene ese `id`, las dos líneas de pedido en el orden enviado con subtotales `21.00` y `3.00`, y `total` igual a `24.00`

#### Scenario: Registro rechazado sin líneas de pedido
- **WHEN** el cliente envía `POST /api/v1/pedidos` con `lineas` vacío o sin el campo `lineas`
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion`
- **AND** no se registra ningún pedido

#### Scenario: Registro rechazado con más de 100 líneas de pedido
- **WHEN** el cliente envía `POST /api/v1/pedidos` con 101 líneas de pedido válidas y SKU distintos
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion`

#### Scenario: Registro rechazado con cuerpo malformado
- **WHEN** el cliente envía `POST /api/v1/pedidos` con un cuerpo que no es JSON válido o con un campo de tipo incorrecto, por ejemplo `cantidad: "dos"`
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:solicitud-malformada`

#### Scenario: Importes devueltos con 2 decimales
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido `{sku: "ABC-1", cantidad: 1, precioUnitario: 3}` y luego `GET /api/v1/pedidos/{id}` con el `id` recibido
- **THEN** el `POST` responde 201 y el `GET` responde 200, y en ambas respuestas `precioUnitario`, `subtotal` y `total` son `3.00`

### Requirement: Validar cada línea de pedido
El sistema MUST validar cada línea de pedido de `POST /api/v1/pedidos`: no es `null`, `sku` es un
texto no vacío de hasta 64 caracteres, `cantidad` es un número JSON entero mayor o igual a 1 y
`precioUnitario` es un decimal mayor que cero con a lo sumo 2 decimales. Si alguna línea de pedido
no es válida, el sistema responde 400 y rechaza el pedido completo; con `type` `validacion`, el
ProblemDetail indica cada campo inválido.

#### Scenario: Línea de pedido en los límites válidos
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `sku` de 64 caracteres, `cantidad` 1 y `precioUnitario` 0.01
- **THEN** el sistema responde 201 y registra el pedido

#### Scenario: Línea de pedido nula
- **WHEN** el cliente envía `POST /api/v1/pedidos` con `lineas: [null]`
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion`
- **AND** no se registra ningún pedido

#### Scenario: Cantidad con decimales
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `cantidad` 2.5
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:solicitud-malformada`
- **AND** no se registra ningún pedido

#### Scenario: Cantidad menor que 1
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `cantidad` 0
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion` que identifica el campo `cantidad` de esa línea de pedido
- **AND** no se registra ningún pedido

#### Scenario: Precio unitario no positivo
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `precioUnitario` 0 o negativo
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion` que identifica el campo `precioUnitario` de esa línea de pedido

#### Scenario: Precio unitario con más de 2 decimales
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `precioUnitario` 10.505
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion` que identifica el campo `precioUnitario` de esa línea de pedido

#### Scenario: SKU vacío o demasiado largo
- **WHEN** el cliente envía `POST /api/v1/pedidos` con una línea de pedido con `sku` vacío, en blanco o de 65 caracteres
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:validacion` que identifica el campo `sku` de esa línea de pedido

### Requirement: Rechazar SKU repetido dentro de un pedido
El sistema MUST rechazar un `POST /api/v1/pedidos` en el que el mismo `sku` aparece en más de una
línea de pedido. La comparación de SKU distingue mayúsculas y minúsculas.

#### Scenario: Pedido con SKU distintos
- **WHEN** el cliente envía `POST /api/v1/pedidos` con líneas de pedido de SKU `"ABC-1"` y `"abc-1"`
- **THEN** el sistema responde 201 y registra el pedido con dos líneas de pedido

#### Scenario: Pedido con SKU repetido
- **WHEN** el cliente envía `POST /api/v1/pedidos` con dos líneas de pedido de SKU `"ABC-1"`
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:sku-duplicado` que indica el SKU repetido
- **AND** no se registra ningún pedido

### Requirement: Consultar un pedido por su identificador
El sistema SHALL devolver un pedido registrado cuando recibe `GET /api/v1/pedidos/{id}`, con
código 200 y los mismos datos que se devolvieron al registrarlo: `id`, líneas de pedido en el orden
original con su subtotal, y total.

#### Scenario: Consulta de un pedido existente
- **WHEN** el cliente registró un pedido y luego envía `GET /api/v1/pedidos/{id}` con el `id` recibido
- **THEN** el sistema responde 200 con el pedido: mismo `id`, mismas líneas de pedido en el mismo orden, subtotales y total iguales a los de la respuesta del registro

#### Scenario: Consulta de un pedido inexistente
- **WHEN** el cliente envía `GET /api/v1/pedidos/{id}` con un UUID que no corresponde a ningún pedido
- **THEN** el sistema responde 404 con un ProblemDetail de `type` `urn:sagant:pedidos:pedido-no-encontrado`

#### Scenario: Consulta con identificador que no es UUID
- **WHEN** el cliente envía `GET /api/v1/pedidos/no-es-un-uuid`
- **THEN** el sistema responde 400 con un ProblemDetail de `type` `urn:sagant:pedidos:solicitud-malformada`
