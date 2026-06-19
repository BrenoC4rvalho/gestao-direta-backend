package br.com.gestaodireta.financial.entity;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.shared.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_categories")
public class FinancialCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(length = 20)
    private String color;

    @Column(length = 60)
    private String icon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @Column(name = "is_default", nullable = false)
    private boolean defaultCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinancialCategoryStatus status;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Farm getFarm() {
        return farm;
    }

    public void setFarm(Farm farm) {
        this.farm = farm;
    }

    public boolean isDefaultCategory() {
        return defaultCategory;
    }

    public void setDefaultCategory(boolean defaultCategory) {
        this.defaultCategory = defaultCategory;
    }

    public FinancialCategoryStatus getStatus() {
        return status;
    }

    public void setStatus(FinancialCategoryStatus status) {
        this.status = status;
    }
}
