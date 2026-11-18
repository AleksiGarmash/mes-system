package com.mes.service;

import com.mes.domain.QualitySpec;
import com.mes.domain.QualitySpecItem;
import com.mes.domain.enums.QcParameterType;
import com.mes.domain.enums.QualitySpecStatus;
import com.mes.dto.request.CreateQualitySpecRequest;
import com.mes.dto.request.QualitySpecItemRequest;
import com.mes.exception.InvalidRequestException;
import com.mes.exception.ResourceNotFoundException;
import com.mes.repository.QualitySpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Управление QC-спецификациями — зеркало {@link RecipeService}: то же версионирование
 * по коду (каждое изменение — новая версия, не правка существующей) и активация
 * с автоматическим архивированием предыдущей активной версии того же кода.
 */
@Service
@RequiredArgsConstructor
public class QualitySpecService {

    private final QualitySpecRepository qualitySpecRepository;

    @Transactional
    public QualitySpec create(CreateQualitySpecRequest request) {
        int nextVersion = qualitySpecRepository.findByCodeOrderByVersionDesc(request.code())
                .stream()
                .findFirst()
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        QualitySpec spec = QualitySpec.builder()
                .code(request.code())
                .name(request.name())
                .version(nextVersion)
                .status(QualitySpecStatus.DRAFT)
                .build();

        for (QualitySpecItemRequest itemReq : request.items()) {
            QcParameterType type = itemReq.parameterType() != null ? itemReq.parameterType() : QcParameterType.NUMERIC;
            if (type == QcParameterType.QUALITATIVE
                    && (itemReq.allowedValues() == null || itemReq.allowedValues().isBlank())) {
                throw new InvalidRequestException(
                        "Качественный параметр «%s»: задайте allowedValues (значения через «;»).".formatted(itemReq.parameterName()));
            }
            if (type == QcParameterType.NUMERIC && itemReq.minValue() == null && itemReq.maxValue() == null) {
                throw new InvalidRequestException(
                        "Числовой параметр «%s»: задайте minValue и/или maxValue.".formatted(itemReq.parameterName()));
            }
            QualitySpecItem item = QualitySpecItem.builder()
                    .parameterCode(itemReq.parameterCode())
                    .parameterName(itemReq.parameterName())
                    .minValue(itemReq.minValue())
                    .maxValue(itemReq.maxValue())
                    .unit(itemReq.unit())
                    .sequenceNo(itemReq.sequenceNo())
                    .parameterType(type)
                    .allowedValues(itemReq.allowedValues())
                    .build();
            spec.addItem(item);
        }

        return qualitySpecRepository.save(spec);
    }

    @Transactional
    public QualitySpec activate(UUID specId) {
        QualitySpec spec = getById(specId);

        qualitySpecRepository.findByCodeAndStatus(spec.getCode(), QualitySpecStatus.ACTIVE)
                .ifPresent(active -> {
                    if (!active.getId().equals(spec.getId())) {
                        active.setStatus(QualitySpecStatus.ARCHIVED);
                        qualitySpecRepository.save(active);
                    }
                });

        spec.setStatus(QualitySpecStatus.ACTIVE);
        return qualitySpecRepository.save(spec);
    }

    @Transactional(readOnly = true)
    public QualitySpec getById(UUID id) {
        return qualitySpecRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QC-спецификация не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public List<QualitySpec> findAllVersions(String code) {
        return qualitySpecRepository.findByCodeOrderByVersionDesc(code);
    }

    @Transactional(readOnly = true)
    public QualitySpec getActiveByCode(String code) {
        return qualitySpecRepository.findByCodeAndStatus(code, QualitySpecStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Активная QC-спецификация не найдена: " + code));
    }

    @Transactional(readOnly = true)
    public Page<QualitySpec> list(Pageable pageable) {
        return qualitySpecRepository.findAll(pageable);
    }
}
