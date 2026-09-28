package com.pharmacy.config;

import com.pharmacy.entity.Medicine;
import com.pharmacy.repository.MedicineRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Sinh 5.000 thuốc mẫu khi DB trống, mô phỏng danh mục lớn. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(MedicineRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            String[] names = {"Panadol", "Amoxicillin", "Ibuprofen", "Vitamin C", "Omeprazole",
                              "Cetirizine", "Metformin", "Loratadine", "Azithromycin", "Berberin"};
            String[] forms = {"Viên nén", "Viên nang", "Siro", "Gói bột", "Viên sủi"};
            List<Medicine> list = new ArrayList<>();
            for (int i = 1; i <= 5000; i++) {
                String base = names[(i - 1) % names.length];   // id=1 là Panadol
                list.add(new Medicine(i <= names.length ? base : base + " " + (100 + i % 900) + "mg #" + i, base,
                        forms[i % forms.length], "Công ty Dược " + (char) ('A' + i % 26),
                        BigDecimal.valueOf(5000 + (i % 200) * 500L), 10 + i % 500));
            }
            repo.saveAll(list);
        };
    }
}
