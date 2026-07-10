package com.example.sideworks.position.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "positiontbl",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_positiontbl_position_name", columnNames = "position_name"),
                @UniqueConstraint(name = "uk_positiontbl_position_order", columnNames = "position_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "position_name", nullable = false, length = 50)
    private String positionName;

    @Column(name = "position_order", nullable = false)
    private Integer positionOrder;


    public static Position create(String positionName, Integer positionOrder) {
        Position position = new Position();
        position.positionName = positionName;
        position.positionOrder = positionOrder;

        return position;
    }

    public void update(String positionName, Integer positionOrder) {
        this.positionName = positionName;
        this.positionOrder = positionOrder;
    }
}
