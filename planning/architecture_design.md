# Propuesta de Arquitectura: Sistema de Inventario de Consistencia Fuerte

## 1. Arquitectura Propuesta: Servicios de Comando/Consulta

**Problema Resuelto:** Se elimina el riesgo de concurrencia en la escritura de stock y la latencia del sistema monolítico, mediante la separación de responsabilidades y la gestión síncrona de las transacciones críticas.

| Servicio | Propósito Clave | Comunicación | Tecnologías Base |
| :--- | :--- | :--- | :--- |
| **Inventory Command Service** | Maneja **escrituras críticas** (Reservar/Actualizar Stock). Es el único punto de verdad. **Prioriza Consistencia Fuerte.** | **RESTful (Síncrona)** para comandos de escritura. | Spring Boot (Java), **Base de Datos Transaccional/Relacional** (Simulada con H2). |
| **Inventory Query Service** | Maneja **lecturas** (Obtener Stock para vista online). **Prioriza Baja Latencia/Disponibilidad.** | **Asíncrona** (Consumiendo eventos de cambio del Command Service). | Spring Boot (Java), Base de Datos de Lectura (simulada o caché). |

## 2. Patrón Clave: CQRS (Command Query Responsibility Segregation)

**Justificación:** Mantenemos la separación para que las consultas de stock (Query) sean rápidas y no bloqueen las transacciones críticas (Command).

### A. Ejecución de Comandos (Escritura Crítica)

* **Prioridad:** Consistencia.
* **Mecanismo:** La aplicación Front-end (simulada) llama al **Inventory Command Service** de forma **RESTful y síncrona** (un solo punto de entrada).
* **Resultado:** La petición REST solo regresa una respuesta HTTP `200 OK` o `409 Conflict` **después** de que la transacción en la base de datos ha sido completada y el bloqueo ha sido liberado, asegurando que el stock es el correcto.

### B. Propagación de Consultas (Lectura/Latencia)

* **Propósito:** Notificar a otros servicios y al Query Service sobre el cambio de stock para actualizar la vista en línea.
* **Mecanismo:** Después de que la transacción de escritura en el **Inventory Command Service** se completa exitosamente, este **publica de forma asíncrona un Evento de Dominio** (`StockUpdatedEvent`) a un *broker* (RabbitMQ/Kafka simulado). El **Inventory Query Service** consume este evento para actualizar su propia vista de lectura.
* **Conclusión:** La escritura es síncrona y segura; la propagación es asíncrona y rápida para la vista.

## 3. Priorización del Teorema CAP (Consistencia Fuerte en la Escritura)

**Objetivo:** Evitar la sobreventa (Condiciones de Carrera).

| Dimensión | Prioridad Elegida | Justificación | Mecanismo de Implementación (Simulado) |
| :--- | :--- | :--- | :--- |
| **Operaciones de Escritura (Comando)** | **Consistencia (C)** | Si dos usuarios intentan comprar el último producto, uno debe ser bloqueado hasta que el otro finalice la transacción. Esto se logra dentro de la transacción. | **Bloqueo a Nivel de Aplicación:** Implementación del patrón de **Bloqueo Pesimista** simulado en el `InventoryService` de Spring Boot, utilizando la funcionalidad de concurrencia de Java (`ReentrantLock` o `@Transactional` con nivel de aislamiento estricto en la BD H2). |
| **Operaciones de Lectura (Query)** | **Disponibilidad (A)** | El stock que ve el cliente puede tener una latencia mínima (ms) respecto al stock real, pero siempre es más rápido y disponible. | Actualización de datos por Eventos Asíncronos (Consistencia Eventual). |

**Conclusión:** La consistencia fuerte se logra mediante un **punto de control de escritura síncrono** (el Command Service) que utiliza bloqueos a nivel de base de datos o aplicación para serializar las operaciones críticas de stock.

## 4. Diseño de la API para Operaciones Clave

La API se enfoca en el recurso `/inventory/items` para manejar las operaciones de stock. La comunicación con el **Inventory Command Service** para la escritura será SÍNCRONA, garantizando la consistencia.

| Operación | Método HTTP | URI (Endpoint) | Propósito y Servicio |
| :--- | :--- | :--- | :--- |
| **1. Obtener Stock** | `GET` | `/inventory/stores/{storeId}/products/{productId}` | **Consulta (Query Service).** Retorna el stock disponible para una combinación específica de tienda y producto. Baja latencia. |
| **2. Reservar Stock** | `POST` | `/inventory/reservations` | **Comando (Command Service).** Realiza una reserva transaccional (compra online). Usa bloqueo pesimista. Devuelve éxito/fallo síncronamente. |
| **3. Actualizar Stock** | `PUT` | `/inventory/stock-updates` | **Comando (Command Service).** Ajustes de stock (recepción de mercancía, inventario físico). Requiere garantía de atomicidad e idempotencia. |

#### 4.1. Request: POST /inventory/reservations (Reserva de Stock)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `transactionId` | String (UUID) | Sí | **Clave de Idempotencia.** Identificador único para evitar doble procesamiento de una misma compra. |
| `productId` | String | Sí | Identificador SKU del producto a reservar. |
| `storeId` | String | Sí | Tienda de la que se reservará el stock. |
| `quantity` | Integer | Sí | Cantidad de unidades a reservar/reducir. |

**Ejemplo de Request:**
```json
{
  "transactionId": "34c1b9b0-8e1c-4b5a-9d6f-7e8c9a0b1d2e",
  "productId": "P12345",
  "storeId": "S001",
  "quantity": 1
}
```

#### 4.2. Request: PUT /inventory/stock-updates (Ajuste/Recepción)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `updateId` | String (UUID) | Sí | **Clave de Idempotencia.** Identificador único del ajuste de inventario. |
| `productId` | String | Sí | Identificador SKU del producto. |
| `storeId` | String | Sí | Tienda afectada. |
| `quantityChange` | Integer | Sí | **Cambio de Cantidad.** Positivo (entrada) o Negativo (salida). |
| `reason` | String | No | Motivo del ajuste (e.g., "Recepción de proveedor", "Ajuste por pérdida"). |

**Ejemplo de Request:**
```json
{
  "updateId": "8a3c4f7b-2a9d-4c3e-8b1a-5d4f6e7c8d9a",
  "productId": "P12345",
  "storeId": "S001",
  "quantityChange": 15,
  "reason": "Recepción de nuevo lote"
}
```

#### 4.3. Códigos de Respuesta HTTP Clave (Command Service)

| Código | Descripción | Operaciones Afectadas |
| :--- | :--- | :--- |
| **200 OK** | La operación (Reserva/Actualización) fue exitosa. La Consistencia fue garantizada. | POST, PUT |
| **400 Bad Request** | La petición está mal formada (ej. faltan campos requeridos). | POST, PUT |
| **404 Not Found** | El producto o la tienda no existe en el sistema. | GET, POST, PUT |
| **409 Conflict** | **Consistencia:** No hay suficiente stock para completar la reserva. (Equivalente a una `StockNotAvailableException`). | POST /reservations |
| **500 Internal Server Error** | Error de base de datos o fallo en el mecanismo de bloqueo. | POST, PUT |

## 5. Estrategia Técnica y Herramientas Seleccionadas

### 5.1 Stack Tecnológico para el Prototipo

| Aspecto | Tecnología Seleccionada | Justificación para el Prototipo |
| :--- | :--- | :--- |
| **Lenguaje/Framework** | **Java 21 y Spring Boot 3+** | Elección del proyecto. Ideal para microservicios robustos con excelente soporte para transacciones y concurrencia. |
| **Persistencia (Simulación)** | **H2 Database (Modo en Memoria/Archivo)** | Cumple con el requisito de base de datos simulada. H2 es compatible con las características de JPA que permiten simular **Bloqueos Pesimistas**, clave para la Consistencia. |
| **Comunicaciones Críticas** | **RESTful Síncrono** | Mecanismo obligatorio para forzar la **Consistencia Fuerte** en las escrituras. El cliente espera la confirmación transaccional del Command Service. |
| **Manejo de Proyectos** | **Maven** | Estándar en Java para la gestión de dependencias (Spring Boot, H2, Testing) y la automatización de la construcción (Build). |
| **Tolerancia a Fallos** | **Resilience4j** | Librería para implementar patrones de resiliencia como **`@Retry`** (reintentos) y **`@CircuitBreaker`** (cortacircuitos) en llamadas simuladas a servicios externos (ej. pago). |
| **Pruebas** | **JUnit 5 / Mockito** | Frameworks estándar para pruebas, cruciales para validar la compleja lógica de concurrencia y transacciones. |

### 5.2 Enfoque de Implementación de Consistencia Fuerte

Para el **Inventory Command Service**, la consistencia se abordará directamente en el `InventoryService` con las siguientes técnicas:

1.  **Bloqueo Pesimista Simulado:** La lógica de negocio utilizará la gestión transaccional de Spring Data JPA (posiblemente con la anotación `@Lock(LockModeType.PESSIMISTIC_WRITE)`) o implementará un mecanismo de **bloqueo a nivel de aplicación** (`ReentrantLock` de Java, mapeado por `productId/storeId`) para garantizar que **solo una petición** pueda modificar el registro de inventario crítico a la vez.
2.  **Transacciones Atómicas:** El uso de `@Transactional` de Spring Boot asegurará que las operaciones de `Reservar Stock` o `Actualizar Stock` sean atómicas. Cualquier fallo, como una cantidad insuficiente, lanzará una excepción que obligará a un *rollback* completo de la base de datos.

Este enfoque de serialización de comandos de escritura elimina el riesgo de condiciones de carrera y sobreventa.