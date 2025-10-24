package com.jr.distributed_inventory_system.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class ExternalPaymentService {

    private final Random random = new Random();

    /**
     * Simula una llamada a un servicio de pago externo.
     * Esta llamada es CRÍTICA para una reserva de stock, pero es propensa a fallos temporales.
     * @Retry: Reintenta la llamada 3 veces si falla.
     * @CircuitBreaker: Si el 50% de las llamadas fallan, 'abre' el circuito y redirige
     * la llamada inmediatamente al método de fallback para no sobrecargar el servicio externo.
     */
    @Retry(name = "paymentRetry", fallbackMethod = "processPaymentFallback")
    @CircuitBreaker(name = "paymentCircuitBreaker", fallbackMethod = "processPaymentFallback")
    public boolean processPayment(String transactionId) {
        // Simulamos un fallo del 30% del tiempo
        if (random.nextDouble() < 0.3) {
            System.err.println("--- PAGO FALLIDO TEMPORALMENTE: SIMULACIÓN ---");
            throw new RuntimeException("Fallo de conexión con el servicio de pagos.");
        }
        System.out.println(">>> PAGO PROCESADO EXITOSAMENTE para transacción: " + transactionId);
        return true;
    }

    /**
     * Método de fallback (alternativa) que se ejecuta si @Retry y @CircuitBreaker fallan.
     */
    public boolean processPaymentFallback(String transactionId, Throwable t) {
        System.err.println("!!! FALLBACK ACTIVADO: El pago no se pudo procesar. El stock debe ser revertido.");
        // En una aplicación real, aquí se revertiría la reserva de stock si ya se hubiera hecho.
        // Para este prototipo, simplemente registramos el fallo.
        return false;
    }
}
