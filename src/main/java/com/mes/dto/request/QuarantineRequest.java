package com.mes.dto.request;

/** Причина карантина (необязательна; по умолчанию «Отзыв лота сырья ...»). */
public record QuarantineRequest(String reason) {
}
