package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springbloom.adapter.out.persistence.entity.FlowerStockEntity;

public interface FlowerStockJpaRepository extends JpaRepository<FlowerStockEntity, Long> {

    Optional<FlowerStockEntity> findBySpeciesId(Long speciesId);

    /**
     * species_key lives on flower_species, so this joins across. The entity holds
     * a plain species_id rather than an association, which keeps the aggregate
     * small at the cost of writing the join by hand.
     */
    @Query(value = """
            SELECT st.*
            FROM flower_stock st
            JOIN flower_species sp ON sp.species_id = st.species_id
            WHERE sp.species_key = :speciesKey
            """, nativeQuery = true)
    Optional<FlowerStockEntity> findBySpeciesKey(@Param("speciesKey") String speciesKey);
}
