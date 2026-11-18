package com.mes.dto.response;

import java.util.List;

/** Итог карантина по лоту: что переведено, что пропущено (уже отбраковано или на карантине). */
public record QuarantineResult(String lotNo, List<String> quarantinedBatchNos, List<String> skippedBatchNos) {
}
