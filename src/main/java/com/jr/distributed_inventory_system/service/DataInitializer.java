package com.jr.distributed_inventory_system.service;


import com.jr.distributed_inventory_system.model.InventoryItem;
import com.jr.distributed_inventory_system.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Clase que se ejecuta al inicio de la aplicación para precargar datos de inventario.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    /**
     * Inyecta como dependencia el inventoryRepository
     * @param inventoryRepository el repo
     */
    public DataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (inventoryRepository.count() == 0) {
            List<InventoryItem> initialInventory = Arrays.asList(
                    createItem("P001", "S001", 10), // Producto con stock crítico
                    createItem("P002", "S001", 50), // Producto con stock normal
                    createItem("P001", "S002", 5)   // Mismo producto en otra tienda
            );
            inventoryRepository.saveAll(initialInventory);
            System.out.println("--- DATOS DE INVENTARIO INICIALES CARGADOS ---");
        }
    }

    private InventoryItem createItem(String productId, String storeId, int stockLevel) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setStoreId(storeId);
        // Generar el ID compuesto
        item.setSkuId(storeId + "_" + productId);
        item.setStockLevel(stockLevel);
        return item;
    }
}