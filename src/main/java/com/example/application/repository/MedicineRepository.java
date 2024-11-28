package com.example.application.repository;

import com.example.application.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    // Add this method
    Medicine findByBrandName(String brandName);
}
