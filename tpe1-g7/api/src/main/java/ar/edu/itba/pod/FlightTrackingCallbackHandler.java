package ar.edu.itba.pod;

public interface FlightTrackingCallbackHandler {

    void onRunwayAssignment(final String flightId, final String runway, final long flightsAhead);

    void onQueuePositionUpdate(final String flightId, final String runway, final long flightsAhead);

    void onDeparture(final String flightId, final String runway);
}
