package com.mes.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Выбор OCR-движка свойством {@code mes.ocr.engine}.
 *
 * <p>Раньше заглушка была {@code @Component} с {@code @ConditionalOnMissingBean}. Эта аннотация
 * надёжна только в автоконфигурациях: при сканировании компонентов порядок регистрации бинов
 * не определён, и заглушка отбрасывалась, а контекст падал без единого {@link OcrEngine}.
 * Явный выбор по свойству не зависит от порядка: реальный движок добавляется своим
 * {@code @Bean} с {@code havingValue = "tesseract"} (или другим именем).
 */
@Configuration
public class OcrConfig {

    @Bean
    @ConditionalOnProperty(name = "mes.ocr.engine", havingValue = "stub", matchIfMissing = true)
    public OcrEngine stubOcrEngine() {
        return new StubOcrEngine();
    }
}
