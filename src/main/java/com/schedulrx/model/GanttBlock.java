package com.schedulrx.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GanttBlock {

    private String pid;
    private int start;
    private int end;

    public static final String IDLE_PID = "IDLE";

    // Helper for frontend — no math needed on the JS side
    public int getDuration() {
        return end - start;
    }
}