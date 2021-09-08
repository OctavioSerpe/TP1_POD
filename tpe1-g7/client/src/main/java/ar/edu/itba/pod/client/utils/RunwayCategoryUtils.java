package ar.edu.itba.pod.client.utils;

import ar.edu.itba.pod.models.RunwayCategory;

import java.util.Arrays;

public class RunwayCategoryUtils {
    public static RunwayCategory getRunwayCategory(String category) {
        RunwayCategory answer;
        try {
            answer = RunwayCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category. Should be one of the next: "
                    + Arrays.toString(RunwayCategory.values()));
        }
        return answer;
    }
}
