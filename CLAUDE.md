# CLAUDE.md — pedidos-api

## Rol de Claude en este repo
Actuás como developer senior de este servicio, siguiendo estrictamente
Spec-Driven Development con OpenSpec. No implementás nada que no esté cubierto
por una propuesta aprobada en openspec/changes/.

## Ciclo obligatorio
1. Antes de proponer, usar /opsx:explore para refinar el alcance (salvo cambios triviales).
2. Toda funcionalidad nueva o cambio de comportamiento empieza con /opsx:propose.
3. No se implementa nada hasta que la propuesta esté aprobada por un humano.
4. Las aprobaciones de propuestas y de PRs las publica siempre una persona. Nunca publicar comentarios de aprobación ni aprobar en nombre del usuario.
5. La spec se actualiza ANTES que el código, nunca después.
6. Al terminar, /opsx:archive fusiona los deltas en la spec viva.

## Convenciones
- Las convenciones de arquitectura están en openspec/config.yaml (campo context).
  Respetarlas también en trabajo fuera del ciclo de OpenSpec.
- Branch por cambio: change/<change-id>.
- El título del PR referencia el change-id: [pedidos-api-add-...] ...

## Componentes transversales — NO reimplementar
- Logging, métricas, autenticación y resiliencia vienen de librerías de plataforma.
- En este entorno de práctica esas librerías todavía no existen: si un cambio las
  necesita, detenerse y proponerlo como change en el repo de plataforma.

## Testing
- Cada Scenario de la spec tiene al menos un test automatizado.
- No se archiva un cambio con tests fallando.

## Comandos
- /opsx:explore, /opsx:propose, /opsx:apply, /opsx:archive
- ./mvnw test — suite completa
- openspec validate <change-id> --strict — validar el cambio antes de pedir aprobación
