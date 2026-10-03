package com.moneygame.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 체결 한 건. kind 로 §5 의 「매수/매도」(BUY 여부)와 「청산 여부」(LIQUIDATION 여부)를 함께 담는다.
 */
@Entity
@Table(name = "trades")
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "tick_index", nullable = false)
    private Integer tickIndex;

    @Column(name = "symbol_label", nullable = false, length = 1)
    private String symbolLabel;

    @Column(name = "symbol_id", nullable = false)
    private Long symbolId;

    /** BUY | SELL | LIQUIDATION | SETTLEMENT */
    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "price", nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "margin", nullable = false, precision = 24, scale = 8)
    private BigDecimal margin;

    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    @Column(name = "fee", nullable = false, precision = 24, scale = 8)
    private BigDecimal fee;

    protected TradeEntity() {
    }

    public TradeEntity(Long gameId, Long participantId, int tickIndex, String symbolLabel, Long symbolId, String kind,
                       long quantity, BigDecimal price, BigDecimal margin, int leverage, BigDecimal fee) {
        this.gameId = gameId;
        this.participantId = participantId;
        this.tickIndex = tickIndex;
        this.symbolLabel = symbolLabel;
        this.symbolId = symbolId;
        this.kind = kind;
        this.quantity = quantity;
        this.price = price;
        this.margin = margin;
        this.leverage = leverage;
        this.fee = fee;
    }

    public Long getId() { return id; }
    public Long getGameId() { return gameId; }
    public Long getParticipantId() { return participantId; }
    public Integer getTickIndex() { return tickIndex; }
    public String getSymbolLabel() { return symbolLabel; }
    public Long getSymbolId() { return symbolId; }
    public String getKind() { return kind; }
    public Long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getMargin() { return margin; }
    public Integer getLeverage() { return leverage; }
    public BigDecimal getFee() { return fee; }
}
