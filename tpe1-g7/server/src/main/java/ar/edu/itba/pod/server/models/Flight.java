package ar.edu.itba.pod.server.models;

import ar.edu.itba.pod.models.RunwayCategory;

import java.time.LocalDateTime;

public class Flight {

    final private RunwayCategory category;
    final private String id;
    final private String airline;
    final private String destinationAirportId;
    private long flightsBeforeDeparture;
    private LocalDateTime departedOn;

    public Flight(final RunwayCategory category, final String id, String airline, final String destinationAirportId) {
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

    public LocalDateTime getDepartedOn() {
        return departedOn;
    }

    public void setDepartedOn(LocalDateTime departedOn) {
        this.departedOn = departedOn;
    }
}
