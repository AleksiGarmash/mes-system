package com.mes.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Лот сырья на складе (срез данных для расчёта выдачи). */
public record StockLot(UUID id, String lotNo, BigDecimal available, LocalDate expiry) {
}
