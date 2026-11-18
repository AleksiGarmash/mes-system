package com.mes.repository;

import com.mes.domain.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, UUID> {
}
