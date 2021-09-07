package ar.edu.itba.pod.exceptions;

public class RunwayAlreadyExistsException extends IllegalArgumentException{
    public RunwayAlreadyExistsException() {
        super("Runway already exists");
    }
}
