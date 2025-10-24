package com.jr.distributed_inventory_system.controller;

import com.jr.distributed_inventory_system.model.InventoryItem;
import com.jr.distributed_inventory_system.service.ExternalPaymentService;
import com.jr.distributed_inventory_system.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ExternalPaymentService externalPaymentService;

    public InventoryController(InventoryService inventoryService, ExternalPaymentService externalPaymentService) {
        this.inventoryService = inventoryService;
        this.externalPaymentService = externalPaymentService;
    }

    // --------------------------------------------------------------------------
    // 1. GET /stock/{storeId}/products/{productId} (QUERY SERVICE SIMULADO)
    // --------------------------------------------------------------------------
    @GetMapping("/stores/{storeId}/products/{productId}")
    public ResponseEntity<InventoryItem> getStock(
            @PathVariable String storeId,
            @PathVariable String productId) {

        return inventoryService.getStockLevel(productId, storeId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stock para Producto " + productId + " en Tienda " + storeId + " no encontrado"
                ));
    }

    // --------------------------------------------------------------------------
    // 2. POST /reservations (COMMAND SERVICE - CONSISTENCIA FUERTE)
    // --------------------------------------------------------------------------
    @PostMapping("/reservations")
    public ResponseEntity<?> reserveStock(@RequestBody Map<String, Object> request) {
        String transactionId = (String) request.get("transactionId");
        String productId = (String) request.get("productId");
        String storeId = (String) request.get("storeId");
        int quantity = (Integer) request.getOrDefault("quantity", 0);

        // **FLUJO CRÍTICO:** Se debe garantizar que el stock solo se reserve si el pago es exitoso.
        // Simulamos la llamada externa con tolerancia a fallos.
        if (externalPaymentService.processPayment(transactionId)) {
            // El pago fue exitoso (o falló pero el retry lo resolvió). Procedemos a reservar el stock.
            InventoryItem reservedItem = inventoryService.reserveStock(productId, storeId, quantity);

            // Si llegamos aquí, la transacción de stock fue exitosa (commit).
            return ResponseEntity.ok(Map.of(
                    "message", "Reserva exitosa y Consistencia garantizada.",
                    "stockLevel", reservedItem.getStockLevel(),
                    "transactionId", transactionId
            ));
        } else {
            // El pago falló incluso con reintentos (fallback activado). La reserva NO SE HACE.
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE) // 503
                    .body(Map.of("message", "Fallo permanente al procesar el pago. Reserva cancelada."));
        }
    }

    // --------------------------------------------------------------------------
    // 3. PUT /stock-updates (COMMAND SERVICE - CONSISTENCIA FUERTE)
    // --------------------------------------------------------------------------
    @PutMapping("/stock-updates")
    public ResponseEntity<InventoryItem> updateStock(@RequestBody Map<String, Object> request) {
        // En una app real, aquí también se usaría un updateId como clave de idempotencia.
        String productId = (String) request.get("productId");
        String storeId = (String) request.get("storeId");
        int quantityChange = (Integer) request.getOrDefault("quantityChange", 0);

        InventoryItem updatedItem = inventoryService.updateStock(productId, storeId, quantityChange);

        return ResponseEntity.ok(updatedItem);
    }
}