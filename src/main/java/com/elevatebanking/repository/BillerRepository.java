package com.elevatebanking.repository;

import com.elevatebanking.entity.Biller;
import com.elevatebanking.entity.enums.BillerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillerRepository extends JpaRepository<Biller, String> {
    List<Biller> findByStatus(BillerStatus status);

    List<Biller> findByCategory(String category);

    Optional<Biller> findByBillerName(String billerName);
}
