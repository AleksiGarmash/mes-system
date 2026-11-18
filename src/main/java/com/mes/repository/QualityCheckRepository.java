package com.mes.repository;

import com.mes.domain.QualityCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QualityCheckRepository extends JpaRepository<QualityCheck, UUID> {
    List<QualityCheck> findByBatchId(UUID batchId);

    /** Ряд числовых результатов параметра по рецептуре в хронологическом порядке (для SPC). */
    @Query("select q from QualityCheck q "
            + "where q.batch.productionOrder.recipe.code = :recipeCode "
            + "and q.parameter = :parameter "
            + "and q.measuredValue is not null "
            + "and q.checkedAt >= :since "
            + "order by q.checkedAt asc")
    List<QualityCheck> findNumericSeries(@Param("recipeCode") String recipeCode,
                                         @Param("parameter") String parameter,
                                         @Param("since") Instant since);
}
