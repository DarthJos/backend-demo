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