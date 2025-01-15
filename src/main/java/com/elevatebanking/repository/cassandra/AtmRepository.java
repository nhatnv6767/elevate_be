package com.elevatebanking.repository.cassandra;

import com.elevatebanking.entity.atm.AtmMachine;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AtmRepository extends CassandraRepository<AtmMachine, String> {
    List<AtmMachine> findByBankCode(String bankCode);

    List<AtmMachine> findByStatus(String status);

    Optional<AtmMachine> findByAtmIdAndBankCode(String atmId, String bankCode);
}
