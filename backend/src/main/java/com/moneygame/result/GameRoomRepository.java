package com.moneygame.result;

import com.moneygame.result.entity.GameRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoomEntity, Long> {
}
