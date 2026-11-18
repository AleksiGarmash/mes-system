package com.mes.dto.response;

import java.util.List;

/** Текущий пользователь и его роли (для отображения интерфейса по ролям). */
public record MeResponse(String username, List<String> roles) {
}
