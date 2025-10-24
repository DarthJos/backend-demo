package com.jr.distributed_inventory_system.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Representa el nivel de stock para un producto en una tienda específica.
 * La clave primaria es una combinación de productId y storeId (clave compuesta).
 * Usamos una sola entidad para simplificar el prototipo.
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"productId", "storeId"})
})
public class InventoryItem {

    // Usamos el ID del producto como la clave principal, pero se necesita un índice compuesto
    // para asegurar la unicidad de la combinación productId/storeId
    @Id
    private String skuId; // SKU o ID único para esta combinación de stock (ej. S001_P12345)

    // Atributos de la clave compuesta simulada
    private String productId;
    private String storeId;

    // Campo crítico de la lógica de negocio
    private int stockLevel;

    // --- Constructor vacío requerido por JPA ---
    public InventoryItem() {
    }

    // --- Getters y Setters ---

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public int getStockLevel() {
        return stockLevel;
    }

    // **CRÍTICO:** Este setter será llamado por la lógica de negocio transaccional.
    public void setStockLevel(int stockLevel) {
        this.stockLevel = stockLevel;
    }
}