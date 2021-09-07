package ar.edu.itba.pod.exceptions;

import java.util.NoSuchElementException;

public class NoSuchRunwayException extends NoSuchElementException {
    public NoSuchRunwayException() {
        super("Runway does not exist");
    }
}
