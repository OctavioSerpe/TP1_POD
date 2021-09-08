package ar.edu.itba.pod.server;

import ar.edu.itba.pod.*;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.DepartureData;
import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.models.Flight;
import ar.edu.itba.pod.server.models.Runway;

import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Servant implements ManagementService, DepartureQueryService, FlightTrackingService, RunwayRequestService {

    // TODO: thread-safe

    final private Map<String, Runway> runwayMap;

    public Servant() {
        runwayMap = new HashMap<>();
    }

    @Override
    public void addRunway(String name, RunwayCategory category) throws RemoteException, RunwayAlreadyExistsException {
        if (runwayMap.containsKey(name))
            throw new RunwayAlreadyExistsException();
        runwayMap.put(name, new Runway(name, category));
    }

    @Override
    public boolean isRunwayOpen(String runwayName) throws RemoteException, NoSuchRunwayException {
        return Optional.ofNullable(runwayMap.get(runwayName)).map(Runway::isOpen).orElseThrow(NoSuchRunwayException::new);
    }

    @Override
    public void openRunway(String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
        if (runway.isOpen())
            throw new IllegalStateException("Runway is already open");
        runway.setOpen(true);
    }

    @Override
    public void closeRunway(String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
        if (!runway.isOpen())
            throw new IllegalStateException("Runway is already closed");
        runway.setOpen(false);
    }

    @Override
    public void issueDeparture() throws RemoteException {
        runwayMap.forEach((runwayName, runway) -> {
            if (runway.isOpen() && !runway.getDepartureQueue().isEmpty()) {
                runway.addToHistory(runway.getDepartureQueue().poll());
            }
            runway.getDepartureQueue().forEach(Flight::incrementFlightsBeforeDeparture);
        });
    }

    @Override
    public void rearrangeDepartures() throws RemoteException {

    }

    @Override
    public void subscribe(FlightTrackingCallbackHandler callbackHandler) throws RemoteException {

    }

    @Override
    public void requestRunway(final String flightId, final String destinationAirportId, final String airlineName, final RunwayCategory minimumCategory) throws RemoteException, NoSuchRunwayException {
        // TODO: chequear el comparator
        final Runway runway = runwayMap.values().stream()
                .filter(r -> r.getCategory().compareTo(minimumCategory) >= 0 && r.isOpen())
                .min(Comparator.comparing(Runway::getCategory).thenComparing(r -> r.getDepartureQueue().size()))
                .orElseThrow(NoSuchRunwayException::new);
        runway.addToQueue(new Flight(runway.getCategory(), flightId, airlineName, destinationAirportId));
    }

    @Override
    public List<DepartureData> getAllDepartures() throws RemoteException {
        return runwayMap.values().stream()
                .flatMap(runway -> runway.getDepartureHistory().stream()
                        .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                runway.getName(),
                                flight.getId(),
                                flight.getDestinationAirportId(),
                                flight.getAirline(),
                                flight.getDepartedOn())))
                .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                .collect(Collectors.toList());
    }

    @Override
    public List<DepartureData> getRunwayDepartures(String runwayName) throws RemoteException, NoSuchRunwayException {
        // TODO: es redundante el sorted, chequear
        return Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new)
                .getDepartureHistory().stream()
                .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                        runwayName,
                        flight.getId(),
                        flight.getDestinationAirportId(),
                        flight.getAirline(),
                        flight.getDepartedOn()))
                .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                .collect(Collectors.toList());
    }

    @Override
    public List<DepartureData> getAirlineDepartures(String airline) throws RemoteException {
        return runwayMap.values().stream()
                .flatMap(runway -> runway.getDepartureHistory().stream()
                        .filter(flight -> flight.getAirline().equals(airline))
                        .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                runway.getName(),
                                flight.getId(),
                                flight.getDestinationAirportId(),
                                flight.getAirline(),
                                flight.getDepartedOn())))
                .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                .collect(Collectors.toList());
    }
}
