package com.mes.dto.response;

import com.mes.domain.QualitySpec;
import com.mes.domain.enums.QualitySpecStatus;

import java.util.UUID;

public record QualitySpecSummary(UUID id, String code, String name, Integer version, QualitySpecStatus status, int itemCount) {

    public static QualitySpecSummary from(QualitySpec s) {
        return new QualitySpecSummary(s.getId(), s.getCode(), s.getName(), s.getVersion(), s.getStatus(), s.getItems().size());
    }
}
