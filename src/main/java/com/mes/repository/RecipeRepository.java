package com.mes.repository;

import com.mes.domain.Recipe;
import com.mes.domain.enums.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    List<Recipe> findByCodeOrderByVersionDesc(String code);
    Optional<Recipe> findByCodeAndStatus(String code, RecipeStatus status);
    Optional<Recipe> findByCodeAndVersion(String code, Integer version);
}
