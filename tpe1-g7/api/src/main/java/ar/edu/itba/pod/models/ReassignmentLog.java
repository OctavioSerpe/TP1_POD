package ar.edu.itba.pod.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReassignmentLog implements Serializable {
    private long assignedCount;
    private final List<String> failed;

    public ReassignmentLog(long assignedCount, List<String> failed) {
        this.assignedCount = assignedCount;
        this.failed = failed;
    }

    public long getAssignedCount() {
        return assignedCount;
    }

    public List<String> getFailed() {
        return failed;
    }

}
