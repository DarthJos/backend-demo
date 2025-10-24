package com.jr.distributed_inventory_system.exception;

/**
 * Excepción lanzada cuando no hay suficiente stock para completar una reserva o actualización.
 * Se mapeará a HTTP 409 Conflict.
 */
public class StockNotAvailableException extends RuntimeException {

    public StockNotAvailableException(String message) {
        super(message);
    }
}
