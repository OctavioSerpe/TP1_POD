package ar.edu.itba.pod.server.models;

import ar.edu.itba.pod.exceptions.NoSuchFlightException;
import ar.edu.itba.pod.models.RunwayCategory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Runway {

    final private Queue<Flight> departureQueue;
    final private List<Flight> departureHistory;
    final private RunwayCategory category;
    final private String name;
    private boolean isOpen;

    public Runway(String name, RunwayCategory category) {
        this.departureQueue = new LinkedList<>();
        this.departureHistory = new LinkedList<>();
        this.category = category;
        this.name = name;
        this.isOpen = true;
    }

    public Queue<Flight> getDepartureQueue() {
        return departureQueue;
    }

    public List<Flight> getDepartureHistory() {
        return departureHistory;
    }

    public RunwayCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public void addToHistory(final Flight flight) {
        departureHistory.add(flight);
    }

    public void addToQueue(final Flight flight) {
        departureQueue.add(flight);
    }

    public long getFlightsAhead(final String flightId) throws NoSuchFlightException {
        long flightsAhead = 0;
        boolean foundFlight = false;

        for (Flight flight : departureQueue) {
            if (flight.getId().equals(flightId)) {
                foundFlight = true;
                break;
            }
            flightsAhead++;
        }

        if (!foundFlight)
            throw new NoSuchFlightException();
        else
            return flightsAhead;
    }
}
