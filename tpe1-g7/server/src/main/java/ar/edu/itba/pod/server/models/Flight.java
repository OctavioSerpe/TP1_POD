package ar.edu.itba.pod.server.models;

import ar.edu.itba.pod.FlightTrackingCallbackHandler;
import ar.edu.itba.pod.models.RunwayCategory;

import java.rmi.RemoteException;
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

    public Flight(final RunwayCategory category, final String id, String airline, final String destinationAirportId,
                  final long flightsBeforeDeparture) {
        this.category = category;
        this.id = id;
        this.airline = airline;
        this.destinationAirportId = destinationAirportId;
        this.flightsBeforeDeparture = 0;
    }

    public void incrementFlightsBeforeDeparture() {
        flightsBeforeDeparture++;
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
