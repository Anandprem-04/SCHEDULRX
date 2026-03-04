package com.schedulrx.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMetrics {

    private String pid;
    private int arrivalTime;
    private int burstTime;
    private int completionTime;
    private int turnaroundTime;   // CT - AT
    private int waitingTime;      // TAT - BT
    private int responseTime;     // First CPU time - AT
}