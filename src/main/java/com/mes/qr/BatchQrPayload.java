package com.mes.qr;

import java.util.UUID;

/**
 * Полезная нагрузка QR-кода, печатаемого на протоколе контроля качества.
 * Привязывает бланк к конкретной физической партии — по аналогии с тем, как
 * {@link OrderQrPayload} привязывает чек-лист производства к заданию.
 */
public record BatchQrPayload(
        UUID batchId,
        String batchNo,
        String recipeCode
) {
}
