package com.jr.distributed_inventory_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jr.distributed_inventory_system.exception.StockNotAvailableException;
import com.jr.distributed_inventory_system.model.InventoryItem;
import com.jr.distributed_inventory_system.service.ExternalPaymentService;
import com.jr.distributed_inventory_system.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest carga el contexto de Spring solo para los controladores y filtros web.
@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc; // Objeto para simular peticiones HTTP

    @Autowired
    private ObjectMapper objectMapper;

    // Sustituye el servicio real con un mock para aislar la prueba
    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private ExternalPaymentService externalPaymentService;

    // --- Prueba 1: GET de Stock Exitoso (200 OK) ---
    @Test
    void whenGetStock_thenReturn200AndItem() throws Exception {
        InventoryItem item = new InventoryItem();
        item.setStockLevel(10);

        when(inventoryService.getStockLevel(anyString(), anyString()))
                .thenReturn(Optional.of(item));

        mockMvc.perform(get("/inventory/stores/S001/products/P001"))
                .andExpect(status().isOk()) // Espera HTTP 200
                .andExpect(jsonPath("$.stockLevel").value(10));
    }

    // --- Prueba 2: GET de Stock No Encontrado (404 Not Found) ---
    @Test
    void whenGetStock_thenReturn404NotFound() throws Exception {
        when(inventoryService.getStockLevel(anyString(), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/inventory/stores/S999/products/P999"))
                .andExpect(status().isNotFound()); // Espera HTTP 404
    }

    // --- Prueba 3: POST Reserva Exitosa (200 OK) ---
    @Test
    void whenReserveStock_thenReturn200() throws Exception {
        InventoryItem reservedItem = new InventoryItem();
        reservedItem.setStockLevel(7); // Stock restante

        // Simular que el pago fue exitoso
        when(externalPaymentService.processPayment(anyString())).thenReturn(true);
        // Simular que la reserva en el servicio fue exitosa
        when(inventoryService.reserveStock(anyString(), anyString(), anyInt()))
                .thenReturn(reservedItem);

        String jsonRequest = "{\"transactionId\": \"T1\", \"productId\": \"P001\", \"storeId\": \"S001\", \"quantity\": 3}";

        mockMvc.perform(post("/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk()) // Espera HTTP 200
                .andExpect(jsonPath("$.stockLevel").value(7))
                .andExpect(jsonPath("$.message").value("Reserva exitosa y Consistencia garantizada."));
    }

    // --- Prueba 4: POST Reserva Falla por Consistencia (409 Conflict) ---
    @Test
    void whenReserveStock_thenReturns409Conflict() throws Exception {
        // Simular que el pago es exitoso, pero la lógica de stock falla por sobreventa
        when(externalPaymentService.processPayment(anyString())).thenReturn(true);
        // El servicio lanza nuestra excepción de negocio
        when(inventoryService.reserveStock(anyString(), anyString(), anyInt()))
                .thenThrow(new StockNotAvailableException("Stock insuficiente para la reserva."));

        String jsonRequest = "{\"transactionId\": \"T2\", \"productId\": \"P001\", \"storeId\": \"S001\", \"quantity\": 12}";

        mockMvc.perform(post("/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isConflict()) // Espera HTTP 409
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Stock insuficiente para la reserva."));
    }
}