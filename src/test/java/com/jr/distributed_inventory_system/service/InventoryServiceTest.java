package com.jr.distributed_inventory_system.service;

import com.jr.distributed_inventory_system.exception.StockNotAvailableException;
import com.jr.distributed_inventory_system.model.InventoryItem;
import com.jr.distributed_inventory_system.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryItem testItem;
    private final String PRODUCT_ID = "P001";
    private final String STORE_ID = "S001";

    @BeforeEach
    void setUp() {
        // Inicializa los mocks para cada prueba
        MockitoAnnotations.openMocks(this);

        testItem = new InventoryItem();
        testItem.setSkuId(STORE_ID + "_" + PRODUCT_ID);
        testItem.setProductId(PRODUCT_ID);
        testItem.setStoreId(STORE_ID);
        testItem.setStockLevel(10); // Stock inicial de 10
    }

    // --- Prueba de Reserva Exitosa ---
    @Test
    void whenReserveStock_thenStockDecreases() {
        // 1. Configurar Mock: Cuando se busca el ítem con bloqueo, retornar el ítem de prueba.
        when(inventoryRepository.findByProductIdAndStoreIdWithLock(PRODUCT_ID, STORE_ID))
                .thenReturn(Optional.of(testItem));
        when(inventoryRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Ejecutar
        int quantityToReserve = 3;
        InventoryItem result = inventoryService.reserveStock(PRODUCT_ID, STORE_ID, quantityToReserve);

        // 3. Verificar
        assertEquals(7, result.getStockLevel(), "El stock debe ser 7 (10 - 3)");
        // Verificar que el método save fue llamado una vez con el nuevo valor
        verify(inventoryRepository, times(1)).save(testItem);
    }

    // --- Prueba de Sobreventa (Consistencia Fuerte) ---
    @Test
    void whenReserveTooMuchStock_thenThrowsExceptionAndNoSave() {
        // 1. Configurar Mock
        when(inventoryRepository.findByProductIdAndStoreIdWithLock(PRODUCT_ID, STORE_ID))
                .thenReturn(Optional.of(testItem));

        // 2. Ejecutar y Verificar la excepción
        int quantityToReserve = 12;
        assertThrows(StockNotAvailableException.class, () -> {
            inventoryService.reserveStock(PRODUCT_ID, STORE_ID, quantityToReserve);
        }, "Debe lanzar StockNotAvailableException al intentar sobreventa");

        // 3. Verificar: El stock NO debe guardarse (rollback simulado por la excepción)
        verify(inventoryRepository, never()).save(any(InventoryItem.class));
        // Verificar que el stock original en el objeto NO cambió (la excepción lo detuvo)
        assertEquals(10, testItem.getStockLevel());
    }

    // --- Prueba de Concurrencia (Simula el Bloqueo Pesimista) ---
    @Test
    void whenMultipleThreadsTryToBuyLastItem_onlyOneSucceeds() throws InterruptedException {
        // NOTA: Esta prueba verifica que la lógica del servicio es correcta.
        // En una prueba de integración se verificaría el bloqueo real de H2.

        // Stock: 1, Cantidad a reservar: 1 en cada hilo.
        testItem.setStockLevel(1);
        
        // Simula el comportamiento secuencial del bloqueo pesimista:
        // El primer hilo obtiene el item con stock=1, lo reduce a 0 y guarda.
        // El segundo hilo obtiene el item con stock=0 (después del save del primero).
        when(inventoryRepository.findByProductIdAndStoreIdWithLock(PRODUCT_ID, STORE_ID))
                .thenReturn(Optional.of(testItem));
        when(inventoryRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation -> {
                    InventoryItem savedItem = invocation.getArgument(0);
                    testItem.setStockLevel(savedItem.getStockLevel());
                    return savedItem;
                });

        int threadsCount = 2; // Dos personas compran a la vez
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch latch = new CountDownLatch(threadsCount);

        // Usaremos un contador para ver cuántas transacciones terminan exitosamente
        int[] successCounter = {0};

        // Sincronizamos para simular el bloqueo pesimista real
        Object lock = new Object();
        
        for (int i = 0; i < threadsCount; i++) {
            executor.submit(() -> {
                try {
                    synchronized (lock) {
                        // Intento de reserva (simulando bloqueo pesimista)
                        inventoryService.reserveStock(PRODUCT_ID, STORE_ID, 1);
                        successCounter[0]++; // Si llega aquí, fue exitoso
                    }
                } catch (StockNotAvailableException ignored) {
                    // Esperado: La segunda transacción falla
                } catch (Exception e) {
                    fail("Se lanzó una excepción inesperada: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Esperar a que ambos hilos terminen
        executor.shutdown();

        // Verificar el resultado esperado
        assertEquals(1, successCounter[0], "Solo una reserva (compra) debe ser exitosa.");
        assertEquals(0, testItem.getStockLevel(), "El stock final debe ser 0.");
        verify(inventoryRepository, times(1)).save(any(InventoryItem.class)); // Solo un save exitoso
    }
}