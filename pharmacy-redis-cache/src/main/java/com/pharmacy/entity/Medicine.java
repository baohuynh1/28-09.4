package com.pharmacy.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "medicines")
public class Medicine implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String activeIngredient;   // Hoạt chất
    private String dosageForm;         // Dạng bào chế
    private String manufacturer;       // Nhà sản xuất
    private BigDecimal price;
    private Integer stock;

    public Medicine() {}

    public Medicine(String name, String activeIngredient, String dosageForm,
                    String manufacturer, BigDecimal price, Integer stock) {
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.dosageForm = dosageForm;
        this.manufacturer = manufacturer;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getActiveIngredient() { return activeIngredient; }
    public void setActiveIngredient(String activeIngredient) { this.activeIngredient = activeIngredient; }
    public String getDosageForm() { return dosageForm; }
    public void setDosageForm(String dosageForm) { this.dosageForm = dosageForm; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
