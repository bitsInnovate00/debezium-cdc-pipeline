package com.debezium.regression.baseline;

import com.debezium.regression.model.Baseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Extended method needed by BaselineController
public interface BaselineServiceExtension {
    List<Baseline> getAllBaselines();
}
