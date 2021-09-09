package ar.edu.itba.pod.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReassignmentLog implements Serializable {
    private long assignedCount;
    private final List<String> failed;

    public ReassignmentLog() {
        this.assignedCount = 0;
        this.failed = new ArrayList<>();
    }

    public long getAssigned() {
        return assignedCount;
    }

    public List<String> getFailed() {
        return failed;
    }

    public void incrementAssigned() {
        assignedCount++;
    }

    public void addToFailed(final String flightId) {
        failed.add(flightId);
    }
}
