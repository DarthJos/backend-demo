# run.md: Guía de Ejecución del Proyecto

## 1. Requisitos Previos

Asegúrate de tener instalado y configurado lo siguiente:
* **JDK 21** o superior.
* **Maven** (para la gestión de dependencias y compilación).
* **Insomnia** o **Postman** (para probar los endpoints REST).

## 2. Ejecución del Backend (Spring Boot)

1.  **Compilación y Paquetización:**
    Abre la terminal en la carpeta raíz del proyecto (`/distributed-inventory-system`).
    ```bash
    mvn clean install
    ```
    *Esto compilará el código y descargará todas las dependencias (incluyendo H2 y Resilience4j).*

2.  **Ejecución del JAR:**
    Ejecuta el archivo JAR generado por Maven:
    ```bash
    java -jar target/distributed-inventory-system-0.0.1-SNAPSHOT.jar
    ```
    *El sistema Spring Boot arrancará en el puerto 8080 y cargará los datos iniciales de stock.*

3.  **Verificación de Stock Inicial:**
    El sistema se inicializa con el siguiente stock (ver `DataInitializer.java`):
    * **P001/S001:** 10 unidades (Stock Crítico)
    * **P002/S001:** 50 unidades
    * **P001/S002:** 5 unidades

## 3. Pruebas con Insomnia/Postman

Una vez que la aplicación esté corriendo, puedes usar estos comandos de `cURL` (o la herramienta gráfica) para interactuar con la API.

### A. Consultar Stock (GET)

* **URL:** `http://localhost:8080/inventory/stores/S001/products/P001`
* **Método:** `GET`
* **Esperado:** `200 OK` con `{"stockLevel": 10}`

### B. Probar la Consistencia (POST Reserva)

* **URL:** `http://localhost:8080/inventory/reservations`
* **Método:** `POST`
* **Cuerpo (JSON):**

```json
{
  "transactionId": "id-unico-12345", 
  "productId": "P001", 
  "storeId": "S001", 
  "quantity": 3
}