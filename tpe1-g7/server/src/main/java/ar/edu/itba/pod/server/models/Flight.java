package ar.edu.itba.pod.server.models;

import ar.edu.itba.pod.FlightTrackingCallbackHandler;
import ar.edu.itba.pod.models.RunwayCategory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public class Flight {

    final private RunwayCategory category;
    final private String id;
    final private String airline;
    final private String destinationAirportId;
    private long flightsBeforeDeparture;
    private LocalDateTime departedOn;
    final private List<FlightTrackingCallbackHandler> callbackHandlers;

    public Flight(final RunwayCategory category, final String id, String airline, final String destinationAirportId) {
        this.category = category;
        this.id = id;
        this.airline = airline;
        this.destinationAirportId = destinationAirportId;
        this.flightsBeforeDeparture = 0;
        callbackHandlers = new LinkedList<>();
    }

    public void incrementFlightsBeforeDeparture() {
        flightsBeforeDeparture++;
    }

    public void addCallbackHandler(final FlightTrackingCallbackHandler callbackHandler) {
        callbackHandlers.add(callbackHandler);
    }

    public void invokeRunwayAssignmentCallbacks(final String runway, final long flightsAhead) {
       callbackHandlers.forEach(callbackHandler -> callbackHandler.onRunwayAssignment(id, runway, flightsAhead));
    }

    public void invokeQueuePositionUpdateCallbacks(final String runway, final long flightsAhead) {
       callbackHandlers.forEach(callbackHandler -> callbackHandler.onQueuePositionUpdate(id, runway, flightsAhead));
    }

    public void invokeDepartureCallbacks(final String runway) {
       callbackHandlers.forEach(callbackHandler -> callbackHandler.onDeparture(id, runway));
    }

    public long getFlightsBeforeDeparture() {
        return flightsBeforeDeparture;
    }

    public String getId() {
        return id;
    }

    public String getAirline() {
        return airline;
    }

    public String getDestinationAirportId() {
        return destinationAirportId;
    }

    public RunwayCategory getCategory() {
        return category;
    }

    public LocalDateTime getDepartedOn() {
        return departedOn;
    }

    public void setDepartedOn(LocalDateTime departedOn) {
        this.departedOn = departedOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Flight flight = (Flight) o;
        return id.equals(flight.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
