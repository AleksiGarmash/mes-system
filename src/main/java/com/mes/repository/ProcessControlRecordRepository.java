package com.mes.repository;

import com.mes.domain.ProcessControlRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProcessControlRecordRepository extends JpaRepository<ProcessControlRecord, UUID> {

    /** Ряд замеров контрольной точки по рецептуре в хронологическом порядке (для SPC). */
    @Query("select r from ProcessControlRecord r "
            + "where r.checklist.productionOrder.recipe.code = :recipeCode "
            + "and r.parameterName = :parameter "
            + "and r.measuredValue is not null "
            + "and r.checklist.confirmedAt >= :since "
            + "order by r.checklist.confirmedAt asc")
    List<ProcessControlRecord> findSeries(@Param("recipeCode") String recipeCode,
                                          @Param("parameter") String parameter,
                                          @Param("since") Instant since);
}
