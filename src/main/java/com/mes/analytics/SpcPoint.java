package com.mes.analytics;

import java.time.Instant;

/** Точка ряда измерений: время, подпись (номер партии), значение и признак выхода за допуск. */
public record SpcPoint(Instant at, String label, double value, boolean outOfSpec) {
}
