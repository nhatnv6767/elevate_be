package com.elevatebanking.repository;

import com.elevatebanking.entity.log.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailedEventRepository extends JpaRepository<FailedEvent, String> {
    List<FailedEvent> findByStatusOrderByFailedAtAsc(String status);
}
