# Sistema de Gestión de Inventario Distribuido - Consistencia Fuerte

## 1. Objetivo del Proyecto

Prototipo de un sistema de gestión de inventario distribuido que resuelve los problemas de **latencia en las actualizaciones** y **sobreventa** (inconsistencia) del sistema monolítico anterior.

El diseño prioriza la **Consistencia Fuerte** en las operaciones de escritura críticas para evitar cualquier condición de carrera en el stock.

## 2. Decisiones Arquitectónicas Clave (Teorema CAP)

| Dimensión | Decisión | Justificación |
| :--- | :--- | :--- |
| **Arquitectura** | **CQRS** (Command Query Responsibility Segregation) | Separa las operaciones de escritura (Command) de las operaciones de lectura (Query). Esto permite que las consultas de stock sean rápidas (Baja Latencia) sin interferir con las transacciones críticas. |
| **Consistencia (Escritura)** | **Prioridad C (Consistencia Fuerte)** | Para las operaciones de **Reserva** y **Actualización** de stock, es **crítico** evitar la sobreventa. Utilizamos un mecanismo **síncrono** con **Bloqueo Pesimista** en la base de datos para serializar las transacciones. |
| **Comunicación Crítica** | **RESTful Síncrono** | El Command Service se comunica con el cliente de forma síncrona. La respuesta solo se emite cuando la transacción (y el bloqueo de stock) ha sido completada, garantizando la Consistencia antes de continuar con la venta. |
| **Tecnología** | **Java 21 / Spring Boot 3 / H2** | Stack estándar para microservicios. H2 se usa para simular una Base de Datos Transaccional con soporte para bloqueos. |
| **Resiliencia** | **Resilience4j** | Implementación de `@Retry` (reintentos) y `@CircuitBreaker` (cortacircuitos) en llamadas simuladas a servicios externos (ej. Pagos) para aumentar la tolerancia a fallos controlada. |

## 3. API de Comandos (Inventario Command Service)

| Operación | Método | Endpoint | Propósito |
| :--- | :--- | :--- | :--- |
| **Consultar Stock** | `GET` | `/inventory/stores/{storeId}/products/{productId}` | Baja Latencia. Retorna el stock actual. |
| **Reservar Stock** | `POST` | `/inventory/reservations` | **CRÍTICO.** Realiza la resta transaccional. Aplica Bloqueo Pesimista. Flujo de Pago simulado con `@Retry`. |
| **Actualizar Stock** | `PUT` | `/inventory/stock-updates` | Ajustes de stock (recepción). También aplica Bloqueo Pesimista para Consistencia. |

### Códigos de Respuesta Clave

| Código | Explicación | Excepción/Condición |
| :--- | :--- | :--- |
| **200 OK** | Operación exitosa. | Transacción completada y stock garantizado. |
| **404 Not Found** | El producto/tienda no existe. | `ResponseStatusException` en el `GET`. |
| **409 Conflict** | **Consistencia Rota.** No hay suficiente stock para la reserva. | `StockNotAvailableException`. |
| **503 Service Unavailable** | El servicio externo (ej. Pago) falló permanentemente. | `Fallback` de Resilience4j activado. |