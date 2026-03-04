package com.schedulrx.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Process {

    private String pid;
    private int arrivalTime;
    private int burstTime;
    private int remainingTime;
    private int priority;
    private int firstExecutionTime = -1;  // -1 = not started yet
    private int completionTime;

    public void initForSimulation() {
        this.remainingTime = this.burstTime;
        this.firstExecutionTime = -1;
        this.completionTime = 0;
    }
}