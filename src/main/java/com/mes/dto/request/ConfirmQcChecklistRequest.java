package com.mes.dto.request;

import java.util.List;

public record ConfirmQcChecklistRequest(
        String labTechnician,
        List<ConfirmQcChecklistItemRequest> corrections,
        String decisionNote,
        String nonconformityActions,
        String secondSignatory
) {
    public ConfirmQcChecklistRequest(String labTechnician, List<ConfirmQcChecklistItemRequest> corrections) {
        this(labTechnician, corrections, null, null, null);
    }
}
