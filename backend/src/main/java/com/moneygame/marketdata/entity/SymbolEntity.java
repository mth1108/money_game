package com.moneygame.marketdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 종목. DDL 원본은 db/schema.sql 이다 (CLAUDE.md §5). */
@Entity
@Table(name = "symbols")
public class SymbolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "market", nullable = false, length = 20)
    private String market;

    protected SymbolEntity() {
    }

    public SymbolEntity(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMarket() { return market; }
}
