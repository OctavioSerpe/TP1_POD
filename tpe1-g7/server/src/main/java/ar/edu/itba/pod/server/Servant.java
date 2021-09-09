package ar.edu.itba.pod.server;

import ar.edu.itba.pod.*;
import ar.edu.itba.pod.exceptions.NoSuchFlightException;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.DepartureData;
import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.models.Flight;
import ar.edu.itba.pod.server.models.Runway;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Servant implements ManagementService, DepartureQueryService, FlightTrackingService, RunwayRequestService {

    // TODO: thread-safe

    final private Map<String, Runway> runwayMap;
    final private Map<String, List<FlightTrackingCallbackHandler>> callbackHandlers;
    final private ExecutorService executor;

    public Servant() {
        runwayMap = new HashMap<>();
        callbackHandlers = new HashMap<>();
        executor = Executors.newCachedThreadPool();
    }

    @Override
    public void addRunway(final String name, final RunwayCategory category) throws RemoteException, RunwayAlreadyExistsException {
        if (runwayMap.containsKey(name))
            throw new RunwayAlreadyExistsException();
        runwayMap.put(name, new Runway(name, category));
    }

    @Override
    public boolean isRunwayOpen(final String runwayName) throws RemoteException, NoSuchRunwayException {
        return Optional.ofNullable(runwayMap.get(runwayName)).map(Runway::isOpen).orElseThrow(NoSuchRunwayException::new);
    }

    @Override
    public void openRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
        if (runway.isOpen())
            throw new IllegalStateException("Runway is already open");
        runway.setOpen(true);
    }

    @Override
    public void closeRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
        if (!runway.isOpen())
            throw new IllegalStateException("Runway is already closed");
        runway.setOpen(false);
    }

    @Override
    public void issueDeparture() throws RemoteException {
        runwayMap.forEach((runwayName, runway) -> {
            // obtener lock de runway
            if (runway.isOpen() && !runway.getDepartureQueue().isEmpty()) {
                Flight departureFlight = runway.getDepartureQueue().poll();
                departureFlight.setDepartedOn(LocalDateTime.now());

                Optional.ofNullable(callbackHandlers.get(departureFlight.getId()))
                        .ifPresent(handlers -> {
                            handlers
                                    .forEach(handler -> executor.submit(() -> {
                                        try {
                                            handler
                                                    .onDeparture(departureFlight.getId(), departureFlight.getDestinationAirportId(), runwayName);
                                        } catch (RemoteException e) {
                                            // TODO: Manejar excepcion bien
                                            e.printStackTrace();
                                        }
                                    }));
                            // TODO: Unexport desde el cliente
                            callbackHandlers.remove(departureFlight.getId());
                        });

                runway.addToHistory(departureFlight);
            }
            runway.getDepartureQueue().forEach(flight -> {
                flight.incrementFlightsBeforeDeparture();
                Optional.ofNullable(callbackHandlers.get(flight.getId()))
                        .ifPresent(handlers -> handlers
                                .forEach(handler -> executor.submit(() -> {
                                    try {
                                        handler
                                                .onQueuePositionUpdate(flight.getId(), flight.getDestinationAirportId(), runwayName, runway.getFlightsAhead(flight.getId()));
                                    } catch (RemoteException e) {
                                        // TODO: Manejar excepcion bien
                                        e.printStackTrace();
                                    }
                                })));
            });
        });
    }

    @Override
    public void rearrangeDepartures() throws RemoteException {
        //TODO: Invocar callbacks de onRunwayAssignment por cada vuelo reasignado
    }

    @Override
    public void subscribe(final String flightId, final String airlineName, final FlightTrackingCallbackHandler handler) throws RemoteException {
        // TODO: Excepcion distinta para cuando no matchea la aerolinea ?
        // TODO: Excepcion distinta para cuando el vuelo EXISTE en el history pero no en la queue (no esta esperando a despegar) ?
        Flight flight = runwayMap.values().stream()
                .flatMap(runway -> runway.getDepartureQueue().stream())
                .filter(f -> f.getId().equals(flightId) && f.getAirline().equals(airlineName))
                .findFirst()
                .orElseThrow(NoSuchFlightException::new);

        List<FlightTrackingCallbackHandler> handlers = callbackHandlers.computeIfAbsent(flightId, k -> new LinkedList<>());
        handlers.add(handler);
    }

    @Override
    public void requestRunway(final String flightId, final String destinationAirportId, final String airlineName, final RunwayCategory minimumCategory)
            throws RemoteException, NoSuchRunwayException {
        // TODO: chequear que no exista el vuelo en algun runway <-- alpedo, no se repiten id de vuelos!

        final Runway runway = runwayMap.values().stream()
                .filter(r -> r.getCategory().compareTo(minimumCategory) >= 0 && r.isOpen())
                .min(Comparator.comparing(Runway::getDepartureQueueSize).thenComparing(Runway::getCategory)
                        .thenComparing(Runway::getName))
                .orElseThrow(NoSuchRunwayException::new);

        final Flight flight = new Flight(runway.getCategory(), flightId, airlineName, destinationAirportId);
        runway.addToQueue(flight);

        Optional.ofNullable(callbackHandlers.get(flight.getId()))
                .ifPresent(handlers -> handlers
                        .forEach(handler -> executor.submit(() -> {
                            try {
                                handler
                                        .onRunwayAssignment(flight.getId(), flight.getDestinationAirportId(), runway.getName(), runway.getFlightsAhead(flight.getId()));
                            } catch (RemoteException e) {
                                // TODO: Manejar excepcion bien
                                e.printStackTrace();
                            }
                        })));
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
    public List<DepartureData> getRunwayDepartures(final String runwayName) throws RemoteException, NoSuchRunwayException {
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
    public List<DepartureData> getAirlineDepartures(final String airline) throws RemoteException {
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
