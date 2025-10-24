package com.jr.distributed_inventory_system.service;

import com.jr.distributed_inventory_system.exception.StockNotAvailableException;
import com.jr.distributed_inventory_system.model.InventoryItem;
import com.jr.distributed_inventory_system.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Inyecta como dependencia el inventoryRepository
     * @param inventoryRepository la interface
     */
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // -------------------------------------------------------------
    // OPERACIONES DE CONSULTA (QUERY) - NO NECESITAN BLOQUEO
    // -------------------------------------------------------------

    /**
     * Retorna el nivel de stock actual para el Query Service.
     * No es transaccional y es rápido (Baja Latencia).
     */
    public Optional<InventoryItem> getStockLevel(String productId, String storeId) {
        return inventoryRepository.findByProductIdAndStoreId(productId, storeId);
    }

    // -------------------------------------------------------------
    // OPERACIONES DE COMANDO (ESCRITURA) - CONSISTENCIA FUERTE
    // -------------------------------------------------------------

    /**
     * Realiza una reserva de stock (resta de unidades).
     * Garantiza la CONSISTENCIA FUERTE mediante Bloqueo Pesimista.
     * @param productId ID del producto.
     * @param storeId ID de la tienda.
     * @param quantityToReserve Cantidad a restar (debe ser > 0).
     */
    @Transactional // Inicia una transacción de BD
    public InventoryItem reserveStock(String productId, String storeId, int quantityToReserve) {
        // 1. Obtener el ítem con Bloqueo Pesimista.
        // Ningún otro proceso puede modificar este registro hasta que esta transacción termine.
        Optional<InventoryItem> itemOptional =
                inventoryRepository.findByProductIdAndStoreIdWithLock(productId, storeId);

        InventoryItem item = itemOptional.orElseThrow(
                () -> new StockNotAvailableException("Producto o tienda no encontrada: " + productId + " en " + storeId)
        );

        int currentStock = item.getStockLevel();

        // 2. Lógica Crítica de Consistencia: Verificar y Actualizar.
        if (currentStock < quantityToReserve) {
            // Lanza la excepción, lo que provocará un ROLLBACK automático de la transacción.
            throw new StockNotAvailableException(
                    "Stock insuficiente. Disponible: " + currentStock + ", Solicitado: " + quantityToReserve
            );
        }

        // 3. Modificación del Stock.
        int newStock = currentStock - quantityToReserve;
        item.setStockLevel(newStock);

        // 4. Guardar (el bloqueo se libera al hacer commit al finalizar el método).
        return inventoryRepository.save(item);
    }

    /**
     * Realiza una actualización general de stock (ajustes, recepciones).
     * También debe ser transaccional para atomicidad.
     * @param productId ID del producto.
     * @param storeId ID de la tienda.
     * @param quantityChange Cantidad a sumar (positivo) o restar (negativo).
     */
    @Transactional
    public InventoryItem updateStock(String productId, String storeId, int quantityChange) {
        // Usamos el bloqueo para cualquier operación de escritura crítica.
        Optional<InventoryItem> itemOptional =
                inventoryRepository.findByProductIdAndStoreIdWithLock(productId, storeId);

        InventoryItem item = itemOptional.orElseGet(() -> {
            // Si el ítem no existe y la cantidad es positiva (nueva recepción), lo creamos.
            if (quantityChange > 0) {
                InventoryItem newItem = new InventoryItem();
                newItem.setProductId(productId);
                newItem.setStoreId(storeId);
                // NOTA: Creamos un SKU ID simple para la clave primaria.
                newItem.setSkuId(storeId + "_" + productId);
                return newItem;
            }
            throw new StockNotAvailableException("Producto o tienda no encontrada para ajuste: " + productId + " en " + storeId);
        });

        int currentStock = item.getStockLevel();
        int newStock = currentStock + quantityChange;

        // Si la actualización es una resta y resulta en stock negativo, lanzamos error.
        if (newStock < 0) {
            throw new StockNotAvailableException(
                    "Ajuste fallido. Stock resultante negativo: " + newStock
            );
        }

        item.setStockLevel(newStock);
        return inventoryRepository.save(item);
    }
}