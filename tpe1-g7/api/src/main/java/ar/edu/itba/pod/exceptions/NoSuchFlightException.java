package ar.edu.itba.pod.exceptions;

import java.util.NoSuchElementException;

public class NoSuchFlightException  extends NoSuchElementException {
    public NoSuchFlightException() {
        super("Flight does not exist");
    }
}
