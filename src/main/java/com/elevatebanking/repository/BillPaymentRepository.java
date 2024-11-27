package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.PaymentStatus;
import com.elevatebanking.entity.transaction.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, String> {
    List<BillPayment> findByAccountId(String accountId);

    List<BillPayment> findByBillerId(String billerId);

    List<BillPayment> findByStatus(PaymentStatus status);

    @Query("SELECT bp FROM BillPayment bp WHERE bp.account.user.id = :userId")
    List<BillPayment> findByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(bp) from BillPayment bp WHERE bp.biller.id = :billerId AND bp.status = 'COMPLETED'")
    Long countCompletedPaymentsByBiller(@Param("billerId") String billerId);
}
