package br.com.gestaodireta.farm.entity;

import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import br.com.gestaodireta.shared.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "farms")
public class Farm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String document;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(name = "total_area", precision = 12, scale = 2)
    private BigDecimal totalArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_type", length = 40)
    private ProductionType productionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FarmStatus status;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public BigDecimal getTotalArea() {
        return totalArea;
    }

    public void setTotalArea(BigDecimal totalArea) {
        this.totalArea = totalArea;
    }

    public ProductionType getProductionType() {
        return productionType;
    }

    public void setProductionType(ProductionType productionType) {
        this.productionType = productionType;
    }

    public FarmStatus getStatus() {
        return status;
    }

    public void setStatus(FarmStatus status) {
        this.status = status;
    }
}
