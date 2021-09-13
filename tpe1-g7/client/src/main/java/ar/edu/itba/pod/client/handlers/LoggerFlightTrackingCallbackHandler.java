package ar.edu.itba.pod.client.handlers;

import ar.edu.itba.pod.FlightTrackingCallbackHandler;

import java.rmi.NoSuchObjectException;
import java.rmi.server.UnicastRemoteObject;

public class LoggerFlightTrackingCallbackHandler implements FlightTrackingCallbackHandler {

    @Override
    public void onRunwayAssignment(final String flightId, final String destination, final String runway, final long flightsAhead) {
        System.out.printf("Flight %s with destiny %s was assigned to runway %s and there are %d flights waiting ahead.\n", flightId, destination, runway, flightsAhead);
    }

    @Override
    public void onQueuePositionUpdate(final String flightId, final String destination, final String runway, final long flightsAhead) {
        System.out.printf("A flight departed from runway %s. Flight %s with destiny %s has %d flights waiting ahead.\n", runway, flightId, destination, flightsAhead);
    }

    @Override
    public void onDeparture(final String flightId, final String destination, final String runway) throws NoSuchObjectException {
        System.out.printf("Flight %s with destiny %s departed on runway %s.\n", flightId, destination, runway);
        UnicastRemoteObject.unexportObject(this, true);
    }
}
