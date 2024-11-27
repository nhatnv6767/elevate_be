package com.elevatebanking.repository;

import com.elevatebanking.entity.Beneficiary;
import com.elevatebanking.entity.enums.BeneficiaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    List<Beneficiary> findByUserId(String userId);

    List<Beneficiary> findByUserIdAndStatus(String userId, BeneficiaryStatus status);

    Optional<Beneficiary> findByUserIdAndAccountNumber(String userId, String accountNumber);

    @Query("SELECT b FROM Beneficiary b WHERE b.user.id = :userId AND b.isFavorite = true")
    List<Beneficiary> findFavoritesByUserId(@Param("userId") String userId);
}
