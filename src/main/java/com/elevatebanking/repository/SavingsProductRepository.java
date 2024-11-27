package com.elevatebanking.repository;

import com.elevatebanking.entity.account.SavingsProduct;
import com.elevatebanking.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SavingsProductRepository extends JpaRepository<SavingsProduct, String> {
    List<SavingsProduct> findByStatus(ProductStatus status);

    @Query("SELECT sp FROM SavingsProduct sp WHERE sp.status = 'ACTIVE' AND sp.minimumDeposit <= :amount order by sp.interestRate desc ")
    List<SavingsProduct> findAvailableProductsForAmount(@Param("amount") BigDecimal amount);

    @Query("SELECT sp FROM SavingsProduct sp WHERE sp.status = 'ACTIVE' AND (:termMonths is null or sp.termMonths = :termMonths)")
    List<SavingsProduct> findByTermMonths(@Param("termMonths") Integer termMonths);
    
}
