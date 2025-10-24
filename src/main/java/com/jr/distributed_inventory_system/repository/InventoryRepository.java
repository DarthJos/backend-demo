package com.jr.distributed_inventory_system.repository;


import com.jr.distributed_inventory_system.model.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Busca un InventoryItem por ProductId y StoreId.
     * @param productId ID del producto (SKU)
     * @param storeId ID de la tienda
     * @return Un Optional que contiene el InventoryItem o vacío si no existe.
     */
    Optional<InventoryItem> findByProductIdAndStoreId(String productId, String storeId);

    /**
     * Método CRÍTICO: Busca un item y aplica un Bloqueo Pesimista (WRITE).
     * Esto asegura que ningún otro proceso pueda leer/escribir este registro
     * hasta que la transacción actual termine (commit o rollback), garantizando
     * la Consistencia Fuerte.
     * @param productId ID del producto
     * @param storeId ID de la tienda
     * @return El InventoryItem bloqueado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findByProductIdAndStoreIdWithLock(String productId, String storeId);
}