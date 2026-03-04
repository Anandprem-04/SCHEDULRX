package com.schedulrx.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class SimulationRequest {

    @NotBlank(message = "Algorithm must be specified")
    private String algorithm;

    // Only required for Round Robin, ignored for others
    @Min(value = 1, message = "Time quantum must be >= 1")
    private Integer quantum;

    @NotNull(message = "Process list cannot be null")
    @Size(min = 1, max = 15, message = "Must provide between 1 and 15 processes")
    @Valid
    private List<ProcessInput> processes;

    @Data
    public static class ProcessInput {

        @NotBlank(message = "Process ID cannot be blank")
        private String pid;

        @Min(value = 0, message = "Arrival time must be >= 0")
        private int arrivalTime;

        @Min(value = 1, message = "Burst time must be >= 1")
        private int burstTime;

        // Optional — only used for Priority scheduling
        private int priority = 0;
    }
}