package com.moneygame.marketdata;

import com.moneygame.marketdata.entity.SymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymbolRepository extends JpaRepository<SymbolEntity, Long> {

    Optional<SymbolEntity> findByCode(String code);
}
