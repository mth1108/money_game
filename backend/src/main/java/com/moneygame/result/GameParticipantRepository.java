package com.moneygame.result;

import com.moneygame.result.entity.GameParticipantEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameParticipantRepository extends JpaRepository<GameParticipantEntity, Long> {

    List<GameParticipantEntity> findByGameIdOrderByFinalRankAscIdAsc(Long gameId);

    /** 사용자 전적. 최근 판이 먼저 (게임 ID 는 종료 순으로 증가한다) */
    List<GameParticipantEntity> findByUserIdOrderByGameIdDesc(Long userId, Pageable page);

    long countByGameId(Long gameId);
}
