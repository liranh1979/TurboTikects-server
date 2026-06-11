package com.turbotikects.turbotikectsserver.entitys;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "hours_of_operation")
@Data
public class HoursOfOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "company_id")
    private Integer companyId;   // null = global

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;       // 0=Sun … 6=Sat

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(name = "slot1_start")
    private String slot1Start;   // "HH:mm", null = start of day

    @Column(name = "slot1_end")
    private String slot1End;     // "HH:mm", null = no break (full shift)

    @Column(name = "slot2_start")
    private String slot2Start;   // "HH:mm", null = no afternoon slot

    @Column(name = "slot2_end")
    private String slot2End;     // "HH:mm"
}
