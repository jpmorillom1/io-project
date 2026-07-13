# Guía completa: Implementación de Human-in-the-Loop (HITL) en LangChain4j Agentic

> **Versión de referencia:** módulo `langchain4j-agentic`
> **Audiencia:** desarrolladores Java que construyen sistemas agénticos y necesitan intervención humana (aprobaciones, datos faltantes, revisión de resultados) dentro del flujo.

---

## Índice

1. [¿Qué es Human-in-the-Loop y por qué lo necesitas?](#1-qué-es-human-in-the-loop-y-por-qué-lo-necesitas)
2. [Modelo conceptual: HITL como agente no-IA](#2-modelo-conceptual-hitl-como-agente-no-ia)
3. [Anatomía del `HumanInTheLoop`](#3-anatomía-del-humanintheloop)
4. [Implementación básica: flujo secuencial con entrada del usuario](#4-implementación-básica-flujo-secuencial-con-entrada-del-usuario)
5. [Ejecución asíncrona: no bloquear al resto del sistema](#5-ejecución-asíncrona-no-bloquear-al-resto-del-sistema)
6. [HITL como fuente de conocimiento en un Blackboard](#6-hitl-como-fuente-de-conocimiento-en-un-blackboard)
7. [Persistencia y recuperación ante crashes (`PendingResponse`)](#7-persistencia-y-recuperación-ante-crashes-pendingresponse)
8. [Completar la respuesta en caliente (sin reiniciar)](#8-completar-la-respuesta-en-caliente-sin-reiniciar)
9. [Gestión del ciclo de vida del `AgenticScope`](#9-gestión-del-ciclo-de-vida-del-agenticscope)
10. [Patrones de integración reales](#10-patrones-de-integración-reales)
11. [Errores comunes y cómo evitarlos](#11-errores-comunes-y-cómo-evitarlos)
12. [Checklist de implementación](#12-checklist-de-implementación)

---

## 1. ¿Qué es Human-in-the-Loop y por qué lo necesitas?

En un sistema agéntico, los agentes de IA ejecutan tareas de forma autónoma. Sin embargo, hay situaciones donde el sistema **debe detenerse y consultar a un humano**:

| Escenario | Ejemplo |
|---|---|
| **Información faltante** | El agente necesita el signo zodiacal del usuario para generar un horóscopo. |
| **Aprobación obligatoria** | Un pedido grande debe ser aprobado por un gerente antes de despacharse. |
| **Revisión de calidad** | Un médico revisa el diagnóstico propuesto por la IA y puede rechazarlo aportando información adicional. |
| **Revisión de código** | El usuario revisa un diff generado por el LLM y acepta/rechaza los cambios antes de continuar. |

Las propiedades ideales de una buena implementación HITL (según la discusión de diseño del propio framework) son:

- **No bloqueante:** el resto de agentes que no dependen de la respuesta humana deben poder seguir ejecutándose.
- **Reanudable:** el flujo continúa exactamente donde quedó cuando llega la respuesta.
- **Recuperable ante crashes:** si el proceso se reinicia mientras espera, el estado no se pierde.
- **Con condición de descarte:** si el humano nunca responde, el estado pendiente debe poder ser evacuado (garbage collection configurable).
- **Interoperable:** invocable por otros agentes/sistemas (por ejemplo vía A2A o endpoints REST).

LangChain4j Agentic cubre estos puntos combinando tres piezas: el agente `HumanInTheLoop`, la ejecución asíncrona con `AsyncResponse`/`PendingResponse`, y la persistencia del `AgenticScope` con checkpoints por paso.

---

## 2. Modelo conceptual: HITL como agente no-IA

En `langchain4j-agentic`, **cualquier clase Java con un único método anotado con `@Agent` es un agente**, aunque no use ningún LLM. El HITL se modela exactamente así: un agente no-IA cuya "inteligencia" es el propio humano.

La forma más esencial (y deliberadamente genérica) es:

```java
public record HumanInTheLoop(Function<AgenticScope, ?> responseProvider) {

    @Agent("An agent that asks the user for missing information")
    public Object askUser(AgenticScope scope) {
        return responseProvider.apply(scope);
    }
}
```

Puntos clave de este diseño:

1. **Recibe el `AgenticScope` completo.** El scope es la pizarra de estado compartido del sistema agéntico. Desde ahí el HITL puede leer cualquier dato producido por agentes anteriores (`scope.readState(...)`) para formular una pregunta con contexto.
2. **Devuelve un valor.** Ese valor se escribe en el scope bajo el `outputKey` configurado, igual que haría cualquier otro agente. Es decir: la respuesta del humano se convierte en un dato más del sistema, consumible por los siguientes agentes.
3. **La `Function<AgenticScope, ?>` (`responseProvider`) encapsula el canal de comunicación.** Puede ser una consola, una UI, un endpoint REST, una cola de mensajes… el framework no impone el medio.

Esta neutralidad es la clave: el framework define el *contrato* (pausa → pregunta con contexto → respuesta al scope), y tú defines el *transporte*.

---

## 3. Anatomía del `HumanInTheLoop`

El módulo provee un builder dedicado, `AgenticServices.humanInTheLoopBuilder()`, con estas piezas:

```java
HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
        .description("...")                 // 1
        .outputKey("...")                   // 2
        .inputKey(String.class, "...")      // 3 (opcional, para planners basados en datos)
        .responseProvider(scope -> ...)     // 4
        .build();
```

| Elemento | Rol | Detalle |
|---|---|---|
| `description(String)` | Describe la capacidad del agente. | Importante cuando el HITL es sub-agente de un **supervisor** o de planners que razonan sobre las descripciones para decidir qué invocar. |
| `outputKey(String)` | Clave del `AgenticScope` donde se escribirá la respuesta del humano. | Los agentes posteriores leen la respuesta desde esta clave. Su elección tiene efectos de diseño profundos (ver §6). |
| `inputKey(Class, String)` | Declara de qué clave del scope **depende** el humano. | En planners dirigidos por datos (Blackboard), esto controla la **activación**: el HITL solo se dispara cuando esa clave existe en el scope. |
| `responseProvider(Function<AgenticScope, ?>)` | La función que efectivamente pregunta y devuelve la respuesta. | Aquí decides el canal (stdin, UI, REST, `PendingResponse`, etc.). |

> **Regla mental:** `inputKey` = *cuándo* se activa el humano; `responseProvider` = *cómo* se le pregunta; `outputKey` = *dónde* aterriza su respuesta.

---

## 4. Implementación básica: flujo secuencial con entrada del usuario

### Caso: horóscopo con dato faltante

Tenemos un agente de IA que genera horóscopos, pero necesita el signo zodiacal, que el usuario no ha proporcionado.

**Paso 1 — El agente de IA:**

```java
public interface AstrologyAgent {
    @SystemMessage("""
        You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
        """)
    @UserMessage("""
        Generate the horoscope for {{name}} who is a {{sign}}.
        """)
    @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
    String horoscope(@V("name") String name, @V("sign") String sign);
}
```

**Paso 2 — El agente HITL** que pide el signo por consola, leyendo el nombre del usuario desde el scope para personalizar la pregunta:

```java
HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
        .description("An agent that asks the zodiac sign of the user")
        .outputKey("sign")   // la respuesta se escribirá como "sign" → la consume AstrologyAgent
        .responseProvider(scope -> {
            System.out.println("Hi " + scope.readState("name") + ", what is your sign?");
            System.out.print("> ");
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                return reader.readLine();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input", e);
            }
        })
        .build();
```

**Paso 3 — Composición en un workflow secuencial:**

```java
AstrologyAgent astrologyAgent = AgenticServices.agentBuilder(AstrologyAgent.class)
        .chatModel(baseModel())
        .outputKey("horoscope")
        .build();

UntypedAgent horoscopeAgent = AgenticServices.sequenceBuilder()
        .subAgents(humanInTheLoop, astrologyAgent)   // primero pregunta, luego genera
        .outputKey("horoscope")
        .build();
```

**Paso 4 — Invocación:**

```java
horoscopeAgent.invoke(Map.of("name", "Mario"));
```

Salida en consola:

```
Hi Mario, what is your sign?
>
```

El flujo se detiene hasta que el usuario escribe su signo; la respuesta se escribe en el scope bajo `"sign"` y la secuencia continúa invocando `AstrologyAgent`, que ya dispone de `name` y `sign`.

### Cómo fluyen los datos (diagrama mental)

```
invoke(name="Mario")
        │
        ▼
┌──────────────────────┐   escribe    ┌──────────────────┐
│  HumanInTheLoop      │ ──"sign"──▶  │   AgenticScope   │
│  (pregunta al user)  │              │ name, sign       │
└──────────────────────┘              └────────┬─────────┘
                                               │ lee name + sign
                                               ▼
                                      ┌──────────────────┐
                                      │  AstrologyAgent  │──▶ "horoscope"
                                      └──────────────────┘
```

---

## 5. Ejecución asíncrona: no bloquear al resto del sistema

Un humano puede tardar segundos, horas o días en responder. Si el HITL se ejecuta de forma síncrona, todo el sistema queda detenido. Por eso el framework **recomienda configurar el agente HITL como asíncrono**: los agentes que **no dependen** de la respuesta humana continúan su ejecución en paralelo, y solo los que consumen el `outputKey` del HITL esperarán a que el valor esté disponible.

Conceptos relevantes:

- **`AsyncResponse`**: comienza a ejecutarse inmediatamente en un pool de hilos. Adecuada cuando la espera es corta y el proceso sigue vivo (p. ej., un diálogo de UI en la misma sesión).
- **`PendingResponse`** (subtipo de `DelayedResponse`): crea un futuro **inicialmente incompleto** que **no consume ningún hilo en segundo plano** y debe completarse explícitamente desde fuera con `complete()`. Es la pieza correcta para esperas largas, integración con sistemas externos y recuperación tras reinicios (ver §7).

> **Criterio de elección:** si la respuesta llega "pronto y en el mismo proceso" → asíncrono con `AsyncResponse`. Si la respuesta puede tardar indefinidamente, llegar por otro canal (REST, cola) o sobrevivir a un reinicio → `PendingResponse` + persistencia.

---

## 6. HITL como fuente de conocimiento en un Blackboard

Este es el patrón más elegante para **revisión con rechazo iterativo**. En un `BlackboardPlanner` los agentes se activan cuando los datos de los que dependen aparecen o cambian en el scope. El HITL participa como un agente más, y la elección de sus `inputKey`/`outputKey` produce un ciclo de revisión natural, **sin lógica de control explícita**.

### Caso: diagnóstico médico con revisor humano

**El truco de diseño:** el revisor depende de `"diagnosis"` (input) pero escribe en `"symptoms"` (output). Cuando rechaza, devuelve los síntomas **enriquecidos**, lo que sobrescribe `"symptoms"` y — por el mecanismo `onStateChanged` del blackboard — **re-dispara automáticamente** a todos los agentes que dependen de los síntomas, produciendo un nuevo diagnóstico que volverá a pasar por revisión.

```java
HumanInTheLoop humanReview = AgenticServices.humanInTheLoopBuilder()
        .description("Review the diagnosis and decide whether to approve or request additional analysis")
        .outputKey("symptoms")                    // ⬅ al rechazar, reescribe los síntomas
        .inputKey(String.class, "diagnosis")      // ⬅ solo se activa cuando hay un diagnóstico
        .responseProvider(scope -> {
            String diagnosis = scope.readState("diagnosis", "");
            String symptoms = scope.readState("symptoms", "");
            if (!isAcceptable(diagnosis)) {
                // RECHAZO: enriquecer síntomas → re-dispara el ciclo diagnóstico
                return symptoms + ". Patient also reports blurred vision.";
            }
            // APROBACIÓN: escribir la clave objetivo y devolver los síntomas sin cambios
            scope.writeState("approvedDiagnosis", diagnosis);
            return symptoms;
        })
        .build();
```

**Wiring con el planner y la condición de terminación:**

```java
MedicalDiagnostics diagnostics = AgenticServices.plannerBuilder(MedicalDiagnostics.class)
        .subAgents(symptomExtractor, labAnalyzer, drugInteraction, diagnosisAgent, humanReview)
        .planner(() -> new BlackboardPlanner(
                scope -> scope.hasState("approvedDiagnosis"),   // objetivo: hay aprobación
                agentOfType(DrugInteractionChecker.class, scope -> {
                            String symptoms = scope.readState("symptoms", "");
                            return symptoms.toLowerCase().contains("medication")
                                    || symptoms.toLowerCase().contains("drug");
                        })
                        .or(declarationOrder())))
        .outputKey("approvedDiagnosis")
        .build();
```

### Detalles de diseño imprescindibles en este patrón

1. **La activación es por datos, no por orden.** `inputKey(String.class, "diagnosis")` garantiza que el revisor no se ejecute hasta que exista un diagnóstico que revisar.
2. **El rechazo no necesita "reintentar" nada explícitamente.** Sobrescribir `"symptoms"` basta: el blackboard reactiva `DrugInteractionChecker`, `DiagnosisAgent`, etc., porque dependen de esa clave.
3. **La aprobación se comunica con una clave distinta.** El `outputKey` del sistema completo se cambió de `"diagnosis"` a `"approvedDiagnosis"` precisamente para que el humano tenga oportunidad de intervenir: el predicado objetivo del planner (`scope.hasState("approvedDiagnosis")`) solo se cumple cuando el revisor escribe esa clave con `scope.writeState(...)`.
4. **El `responseProvider` puede tener lógica de decisión.** No es solo "leer una respuesta": puede combinar validación automática (`isAcceptable`) con la intervención humana real.

```
        ┌──────────────┐   symptoms    ┌──────────────┐   diagnosis
input ─▶│ symptomExtr. │──────────────▶│ diagnosisAg. │──────────────┐
        └──────────────┘        ▲      └──────────────┘              ▼
                                │                             ┌──────────────┐
                                │  RECHAZO: symptoms++        │ humanReview  │
                                └─────────────────────────────│  (HITL)      │
                                                              └──────┬───────┘
                                                    APROBACIÓN       │
                                                    approvedDiagnosis▼  → objetivo cumplido → done
```

---

## 7. Persistencia y recuperación ante crashes (`PendingResponse`)

Esta es la parte crítica para HITL de larga duración: el humano puede responder **mañana**, y tu proceso puede haberse reiniciado tres veces mientras tanto. El framework lo soporta con **recoverabilidad** basada en dos mecanismos:

1. **Checkpoint por paso:** tras cada invocación de agente, el `AgenticScope` completo (todo lo escrito con `writeState`) se persiste automáticamente en el store configurado.
2. **Persistencia del estado del planner:** el bucle de ejecución guarda la posición interna del planner (p. ej., en qué paso de una secuencia va), de modo que al recuperar, el workflow **reanuda desde el paso correcto** en lugar de reejecutar desde cero.

### 7.1. Configurar el store de persistencia

`AgenticScope` y su registro son estructuras en memoria por defecto. Para durabilidad, implementa la SPI `AgenticScopeStore` y regístrala de una de dos formas:

**Programáticamente:**

```java
AgenticScopePersister.setStore(new MyAgenticScopeStore());
```

**Vía Java Service Provider:** crea el archivo
`META-INF/services/dev.langchain4j.agentic.scope.AgenticScopeStore`
con el nombre totalmente cualificado de tu implementación.

> Tu implementación decide el backend: base de datos, sistema de archivos, Redis, etc.

### 7.2. Planners que participan en la recuperación

Un `Planner` puede exponer/restaurar su estado interno mediante dos métodos opcionales:

```java
// Devuelve el estado interno actual del planner para persistirlo
default Map<String, Object> executionState() { return Map.of(); }

// Restaura el estado interno desde un mapa previamente guardado
default void restoreExecutionState(Map<String, Object> state) { }
```

- Los planners **con estado** (secuencial, loop) los implementan para guardar cursor y contadores de iteración.
- Los planners **sin estado** (`ParallelPlanner`, `ConditionalPlanner`) usan los no-op por defecto.
- Tus planners **custom** pueden sobrescribirlos para participar en la recuperación.

### 7.3. Ejemplo completo: aprobación de pedidos con reinicio del proceso

**Workflow:** validar pedido → esperar aprobación humana → despachar.

**Paso 0 — La interfaz raíz.** El `@MemoryId` es **esencial**: activa el scope persistente, requisito de la recuperabilidad. Extender `AgenticScopeAccess` te da acceso al scope por ID.

```java
public interface OrderWorkflow extends AgenticScopeAccess {
    @Agent
    String processOrder(@MemoryId String orderId, @V("order") String orderDetails);
}
```

**Paso 1 — Validación (agente no-IA vía `agentAction`):**

```java
AgenticScopeAction validateOrder = AgenticServices.agentAction(scope -> {
    String order = scope.readState("order", "");
    scope.writeState("validated_order", "VALIDATED: " + order);
});
```

**Paso 2 — La compuerta humana con `PendingResponse`.** En vez de bloquear un hilo, el `responseProvider` devuelve un `PendingResponse` identificado por una clave (`"manager-approval"`). Es un futuro incompleto, sin hilo de fondo, completable desde fuera. Tras serializar/deserializar, se recrea un futuro incompleto nuevo, permitiendo que un sistema externo **se reconecte** y lo complete.

```java
HumanInTheLoop approvalGate = AgenticServices.humanInTheLoopBuilder()
        .description("Wait for manager approval on large orders")
        .outputKey("approval")
        .responseProvider(scope -> new PendingResponse<>("manager-approval"))
        .build();
```

**Paso 3 — Despacho final:**

```java
AgenticScopeAction shipOrder = AgenticServices.agentAction(scope -> {
    String validated = scope.readState("validated_order", "");
    String approval = scope.readState("approval", "");
    scope.writeState("result", "Order " + validated + " — " + approval);
});
```

**Wiring:**

```java
OrderWorkflow workflow = AgenticServices.sequenceBuilder(OrderWorkflow.class)
        .subAgents(validateOrder, approvalGate, shipOrder)
        .outputKey("result")
        .build();
```

**Qué pasa al ejecutar:** el workflow valida el pedido y **se detiene** en el paso HITL esperando entrada externa. En ese instante, se checkpointea al store el scope completo: los datos del pedido validado, la posición del planner (paso 2 completado) y el propio `PendingResponse`.

**Recuperación tras crash/reinicio:**

```java
// Tras el reinicio: cargar el scope persistido y proveer la respuesta humana
AgenticScope recovered = workflow.getAgenticScope("order-12345");

// Sustituir el PendingResponse por la decisión real del humano
recovered.writeState("approval", "APPROVED by manager");

// Re-invocar con el mismo ID de pedido — el planner reanuda desde el paso 3
String result = workflow.processOrder("order-12345", "1000 widgets");
// → "Order VALIDATED: 1000 widgets — APPROVED by manager"
```

El `SequentialPlanner` restaura su cursor desde el checkpoint y **salta los pasos ya completados** (validación y compuerta de aprobación), ejecutando solo el despacho final.

---

## 8. Completar la respuesta en caliente (sin reiniciar)

Si el proceso sigue vivo y el workflow simplemente está esperando la entrada humana, no hace falta re-invocar nada: se puede completar el `PendingResponse` directamente — por ejemplo desde un endpoint REST que reciba la decisión del gerente:

```java
// Completar la respuesta pendiente en vuelo (p. ej., desde un endpoint REST)
AgenticScope scope = workflow.getAgenticScope("order-12345");
scope.completePendingResponse("manager-approval", "APPROVED by manager");
```

Esto **desbloquea el hilo en espera** y el workflow continúa al paso de despacho sin ningún reinicio.

### Esqueleto de integración REST (patrón típico)

```java
// POST /orders/{orderId}/approval
@POST
@Path("/orders/{orderId}/approval")
public Response approve(@PathParam("orderId") String orderId, ApprovalDto dto) {
    AgenticScope scope = workflow.getAgenticScope(orderId);
    if (scope == null) {
        return Response.status(404).build();   // scope evacuado o inexistente
    }
    scope.completePendingResponse("manager-approval", dto.decision());
    return Response.accepted().build();
}
```

Con este patrón, el HITL cumple el requisito de ser **invocable desde otros sistemas** (una UI web, otro servicio, incluso otro agente vía A2A) — el canal de respuesta queda desacoplado del canal de pregunta.

---

## 9. Gestión del ciclo de vida del `AgenticScope`

Esto es crucial para el requisito de "garbage collection cuando la respuesta nunca llega":

- **Sin memoria** (`@MemoryId` ausente): el `AgenticScope` es transitorio y se descarta automáticamente al final de la ejecución.
- **Con memoria** (`@MemoryId` presente): el scope se guarda en un registro interno y **permanece ahí para siempre** para permitir interacción conversacional/con estado. Por tanto, **evacuarlo es responsabilidad tuya**.

Para poder evacuar, el agente raíz debe implementar `AgenticScopeAccess`:

```java
agent.evictAgenticScope(memoryId);
```

**Recomendación práctica para HITL de larga duración:** implementa una política de expiración (tu "trashing condition") en tu capa de integración — p. ej., un job programado que evacúe scopes cuyos `PendingResponse` lleven más de N días sin completarse, notificando/cancelando el pedido correspondiente. El framework te da el mecanismo (`evictAgenticScope` + tu `AgenticScopeStore`); la política es tuya.

---

## 10. Patrones de integración reales

### 10.1. Consola / CLI (síncrono, sesiones cortas)
`responseProvider` que lee de `System.in` (§4). Simple, ideal para prototipos y herramientas de línea de comandos.

### 10.2. UI de escritorio / IDE (tool que bloquea hasta aceptar/rechazar)
Patrón validado en la práctica (p. ej., revisión de diffs de código en un IDE): el `responseProvider` construye la UI (un diff viewer), y **solo retorna cuando el usuario acepta o rechaza**. Como el protocolo con el LLM es stateless, no se necesitan callbacks ni notificaciones: cuando tienes algo nuevo que decirle al modelo, simplemente continúas el flujo. Si la espera puede ser larga, configúralo asíncrono.

### 10.3. Servicio web (asíncrono + persistente)
La combinación `PendingResponse` + `AgenticScopeStore` + endpoint REST (§7–§8). Es el patrón correcto para aprobaciones de negocio que tardan horas/días y deben sobrevivir despliegues y reinicios.

### 10.4. Revisión iterativa dirigida por datos
HITL dentro de un `BlackboardPlanner` con `outputKey` que sobrescribe la entrada de los agentes productores (§6). El bucle revisar → rechazar → regenerar → revisar emerge del propio grafo de dependencias, sin control flow explícito.

### 10.5. Adaptación de tipos alrededor del HITL
La respuesta humana suele llegar como `String`. Si el siguiente agente espera otro tipo, intercala un `agentAction` adaptador:

```java
UntypedAgent flow = AgenticServices.sequenceBuilder()
        .subAgents(
                hitlScorer,   // el humano escribe "score" como String
                AgenticServices.agentAction(scope ->
                        scope.writeState("score",
                                Double.parseDouble(scope.readState("score", "0.0")))),
                reviewer)     // consume "score" como double
        .build();
```

---

## 11. Errores comunes y cómo evitarlos

| # | Error | Consecuencia | Solución |
|---|---|---|---|
| 1 | Olvidar `@MemoryId` en la interfaz raíz cuando se quiere recuperabilidad. | No hay scope persistente → nada que recuperar tras un crash. | Añadir `@MemoryId` al método `@Agent` raíz; es lo que activa el scope persistente. |
| 2 | Configurar `PendingResponse` **sin** un `AgenticScopeStore`. | Al reiniciar, el estado en memoria se pierde igualmente. | Registrar el store con `AgenticScopePersister.setStore(...)` o vía SPI. |
| 3 | HITL síncrono en flujos con ramas independientes. | Todo el sistema queda bloqueado esperando al humano. | Configurar el HITL como asíncrono; solo los consumidores de su `outputKey` esperarán. |
| 4 | En Blackboard, dar al revisor un `outputKey` "inerte" (que nadie consume). | El rechazo no re-dispara nada; el ciclo de revisión no ocurre. | Hacer que el `outputKey` del HITL sea una clave de la que **dependen** los agentes que deben re-ejecutarse (p. ej. `"symptoms"`). |
| 5 | Usar la misma clave como salida final del sistema y como salida del agente productor. | El humano nunca tiene oportunidad de vetar: el objetivo se cumple antes de la revisión. | Introducir una clave de aprobación distinta (`"approvedDiagnosis"`) que **solo** escribe el HITL, y usarla en el predicado objetivo y en el `outputKey` del sistema. |
| 6 | No evacuar nunca los scopes con memoria. | Fuga: el registro crece indefinidamente con esperas huérfanas. | Implementar `AgenticScopeAccess` y una política de expiración que llame a `evictAgenticScope(id)`. |
| 7 | Usar `AsyncResponse` para esperas de días. | Ocupa recursos y no sobrevive reinicios. | Usar `PendingResponse` (futuro incompleto, sin hilo de fondo, re-conectable tras deserialización). |
| 8 | Asumir que la respuesta humana es del tipo esperado por el siguiente agente. | `ClassCastException` / parsing implícito fallido. | Intercalar un `agentAction` adaptador (§10.5) o validar/convertir dentro del `responseProvider`. |
| 9 | Poner lógica de negocio pesada dentro del `responseProvider`. | Acopla el canal humano con las reglas del dominio; difícil de testear. | El provider debe limitarse a preguntar/recibir; las decisiones automáticas simples (`isAcceptable`) están bien, el resto va en agentes/acciones dedicados. |

---

## 12. Checklist de implementación

**Diseño**
- [ ] Identificar el punto exacto de intervención humana (dato faltante, aprobación o revisión).
- [ ] Decidir el `outputKey`: ¿la respuesta humana es un dato nuevo, o sobrescribe una entrada para re-disparar agentes (patrón Blackboard)?
- [ ] Si hay revisión con rechazo, definir una clave de aprobación separada y usarla como objetivo/salida del sistema.
- [ ] Decidir el canal: consola, UI bloqueante, REST + `PendingResponse`.

**Ejecución**
- [ ] Construir el HITL con `AgenticServices.humanInTheLoopBuilder()` (description, outputKey, inputKey si aplica, responseProvider).
- [ ] Configurarlo asíncrono si hay agentes que no dependen de la respuesta.
- [ ] Para planners dirigidos por datos, declarar `inputKey` para controlar la activación.

**Durabilidad (si la espera puede ser larga)**
- [ ] Añadir `@MemoryId` a la interfaz raíz y extender `AgenticScopeAccess`.
- [ ] Implementar y registrar un `AgenticScopeStore`.
- [ ] Usar `PendingResponse<>("clave")` en el `responseProvider`.
- [ ] Exponer un endpoint/mecanismo externo que llame a `scope.completePendingResponse("clave", respuesta)` (proceso vivo) o `recovered.writeState(...)` + re-invocación (tras reinicio).
- [ ] Si el planner es custom, implementar `executionState()` / `restoreExecutionState(...)`.

**Ciclo de vida**
- [ ] Definir la condición de descarte (timeout de negocio) y evacuar con `evictAgenticScope(id)`.
- [ ] Probar el escenario de crash: matar el proceso a mitad de la espera y verificar que la recuperación reanuda en el paso correcto.

---

## Resumen en una frase

En LangChain4j Agentic, el humano **es un agente más**: se activa cuando sus datos de entrada existen (`inputKey`), pregunta por el canal que tú definas (`responseProvider`), y su respuesta se convierte en estado compartido (`outputKey`) que hace avanzar — o repetir — el resto del sistema; y con `@MemoryId` + `AgenticScopeStore` + `PendingResponse`, esa espera humana se vuelve no bloqueante, persistente y recuperable ante cualquier reinicio.
