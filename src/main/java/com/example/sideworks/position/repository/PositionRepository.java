package com.example.sideworks.position.repository;

import com.example.sideworks.position.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findAllByOrderByPositionOrderAsc();

    boolean existsByPositionName(String positionName);

    boolean existsByPositionOrder(Integer positionOrder);
}
