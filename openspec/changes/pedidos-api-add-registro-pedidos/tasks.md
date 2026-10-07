# Tasks

## 1. Preparación

- [x] 1.1 Crear la branch `change/pedidos-api-add-registro-pedidos` desde `main` y verificar con `git branch --show-current`
- [ ] 1.2 Agregar `spring.jpa.open-in-view=false` en `src/main/resources/application.properties` y verificar que `./mvnw test` sigue en verde y que el log de arranque ya no muestra el warning de open-in-view

## 2. Manejo de errores centralizado

- [ ] 2.1 Crear `com.sagant.pedidos.error` con las constantes de `type` (`validacion`, `solicitud-malformada`, `sku-duplicado`, `pedido-no-encontrado`) y el `@RestControllerAdvice` único que mapea validación y cuerpo o parámetro malformado a ProblemDetail; verificar que compila con `./mvnw test`
- [ ] 2.2 Agregar a `@RestControllerAdvice` los mapeos de `SkuDuplicadoException` (con la extensión `sku`) y `PedidoNoEncontradoException`; verificar con los tests de los grupos 4 y 5, que cubren cada `type`

## 3. Modelo y persistencia

- [ ] 3.1 Crear `Pedido` (`@Entity`, id UUID) y `LineaPedido` (`@Embeddable`, `@ElementCollection` + `@OrderColumn`, `precioUnitario` `BigDecimal` con scale 2) en `pedido.model`, y `PedidoRepository` en `pedido.repository`; verificar con un test `@DataJpaTest` que guarda y recupera un pedido conservando el orden de las líneas de pedido
- [ ] 3.2 Crear los records `RegistrarPedidoRequest`, `LineaPedidoRequest`, `PedidoDto` y `LineaPedidoDto` en `pedido.dto` con las anotaciones de Bean Validation del design, incluido `List<@NotNull @Valid LineaPedidoRequest> lineas`; verificar que compila

## 4. Registro de pedidos

- [ ] 4.1 Implementar `PedidoService.registrar` (`@Transactional`): rechaza SKU repetido, persiste y mapea a `PedidoDto` con el método de mapeo único del design, que normaliza `precioUnitario`, `subtotal` y `total` a escala 2 con `RoundingMode.UNNECESSARY`; verificar con un test unitario del mapeo que, para `precioUnitario` 3 con escala 0, devuelve `3.00` en los tres importes
- [ ] 4.2 Implementar `POST /api/v1/pedidos` en `PedidoController`, que devuelve 201 con `Location`; verificar con los tests de 4.3 a 4.6
- [ ] 4.3 Test MockMvc del Scenario "Registro exitoso de un pedido con dos líneas de pedido"
- [ ] 4.4 Test MockMvc del Scenario "Registro rechazado sin líneas de pedido": cubre `lineas` vacío y `lineas` ausente
- [ ] 4.5 Test MockMvc del Scenario "Registro rechazado con más de 100 líneas de pedido"
- [ ] 4.6 Test MockMvc del Scenario "Registro rechazado con cuerpo malformado": cubre JSON inválido y `cantidad: "dos"`

## 5. Validación de líneas de pedido y SKU repetido

- [ ] 5.1 Test MockMvc del Scenario "Línea de pedido en los límites válidos"
- [ ] 5.2 Test MockMvc del Scenario "Cantidad menor que 1": verifica que `errores` identifica `lineas[0].cantidad` y que no se registra ningún pedido
- [ ] 5.3 Test MockMvc del Scenario "Precio unitario no positivo": cubre 0 y negativo
- [ ] 5.4 Test MockMvc del Scenario "Precio unitario con más de 2 decimales"
- [ ] 5.5 Test MockMvc del Scenario "SKU vacío o demasiado largo": cubre vacío, en blanco y 65 caracteres
- [ ] 5.6 Test MockMvc del Scenario "Pedido con SKU distintos"
- [ ] 5.7 Test MockMvc del Scenario "Pedido con SKU repetido": verifica el `type` `sku-duplicado`, la extensión `sku` y que no se registra ningún pedido
- [ ] 5.8 Test MockMvc del Scenario "Línea de pedido nula": envía `lineas: [null]`, verifica 400 con `type` `validacion` (no 500) y que no se registra ningún pedido
- [ ] 5.9 Test MockMvc del Scenario "Cantidad con decimales": envía `cantidad` 2.5 y verifica 400 con `type` `solicitud-malformada` y que no se registra ningún pedido. Si falla porque Jackson acepta el decimal, desactivar `ACCEPT_FLOAT_AS_INT` como indica el design y verificar que el test pasa

## 6. Consulta de pedidos

- [ ] 6.1 Implementar `PedidoService.obtener` (`@Transactional(readOnly = true)`, lanza `PedidoNoEncontradoException`) y `GET /api/v1/pedidos/{id}` en `PedidoController`; verificar con los tests de 6.2 a 6.5
- [ ] 6.2 Test MockMvc del Scenario "Consulta de un pedido existente": registra un pedido, sigue el `Location` y compara con la respuesta del registro
- [ ] 6.3 Test MockMvc del Scenario "Consulta de un pedido inexistente"
- [ ] 6.4 Test MockMvc del Scenario "Consulta con identificador que no es UUID"
- [ ] 6.5 Test MockMvc del Scenario "Importes devueltos con 2 decimales": envía `precioUnitario` 3 y verifica `3.00` en `precioUnitario`, `subtotal` y `total` tanto en la respuesta del `POST` como en la del `GET` (se ubica en este grupo porque necesita el `GET`)

## 7. Verificación integral

- [ ] 7.1 Ejecutar `./mvnw test` completo y verificar que está en verde
- [ ] 7.2 Verificar que `/swagger-ui.html` muestra `POST /api/v1/pedidos` y `GET /api/v1/pedidos/{id}` con los esquemas de request y response

## 8. Cierre

- [ ] 8.1 Ejecutar `openspec validate pedidos-api-add-registro-pedidos --strict` sin errores
- [ ] 8.2 Pasar el PR #2 (`[pedidos-api-add-registro-pedidos] Registro y consulta de pedidos`, hoy en borrador) a listo para revisión, con la plantilla completa y el checklist revisado
- [ ] 8.3 Obtener la aprobación de los aprobadores requeridos de la proposal y mergear
- [ ] 8.4 Tras el merge, ejecutar `/opsx:archive` con los tests en verde y verificar que `openspec/specs/pedidos/spec.md` existe
