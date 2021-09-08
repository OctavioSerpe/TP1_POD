package ar.edu.itba.pod.models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class DepartureData implements Serializable {

    // FIXME: por decision de diseño no se expone flight al usuario sino esta clase pojo
    final private long flightsBeforeDeparture;
    final private String runwayName;
    final private String flightId;
    final private String destinationAirportId;
    final private String airline;
    final private LocalDateTime departedOn;

    public DepartureData(long flightsBeforeDeparture, String runwayName, String flightId, String destinationAirportId, String airline, LocalDateTime departedOn) {
        this.flightsBeforeDeparture = flightsBeforeDeparture;
        this.runwayName = runwayName;
        this.flightId = flightId;
        this.destinationAirportId = destinationAirportId;
        this.airline = airline;
        this.departedOn = departedOn;
    }

    public long getFlightsBeforeDeparture() {
        return flightsBeforeDeparture;
    }

    public String getRunwayName() {
        return runwayName;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getDestinationAirportId() {
        return destinationAirportId;
    }

    public String getAirline() {
        return airline;
    }

    public LocalDateTime getDepartedOn() {
        return departedOn;
    }
}
