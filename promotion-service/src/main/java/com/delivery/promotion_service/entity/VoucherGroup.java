package com.delivery.promotion_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "voucher_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    // e.g. Max 1 voucher of this group per order
    @Column(nullable = false)
    @Builder.Default
    private Integer maxPerOrder = 1;

    // List of Group IDs that are mutually exclusive with this group
    // Example: If Group 1 has "2" in this list, we cannot apply Group 1 and Group 2 together.
    @ElementCollection
    @CollectionTable(name = "voucher_group_exclusions", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "excluded_group_id")
    @Builder.Default
    private List<Long> excludedGroupIds = new ArrayList<>();
}
