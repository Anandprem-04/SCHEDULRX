package com.schedulrx.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResponse {

    private String algorithmUsed;
    private Integer quantumUsed;  // null for non-RR algorithms

    // Gantt chart blocks in execution order
    private List<GanttBlock> ganttBlocks;

    // Per-process metrics table
    private List<ProcessMetrics> metrics;

    // Summary averages — this is where Avg WT and Avg TAT live
    private double averageWT;
    private double averageTAT;
    private double averageRT;
}
