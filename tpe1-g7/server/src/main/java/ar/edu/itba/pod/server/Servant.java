package ar.edu.itba.pod.server;

import ar.edu.itba.pod.*;
import ar.edu.itba.pod.exceptions.NoSuchFlightException;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.DepartureData;
import ar.edu.itba.pod.models.ReassignmentLog;
import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.models.Flight;
import ar.edu.itba.pod.server.models.Runway;

import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Servant implements ManagementService, DepartureQueryService, FlightTrackingService, RunwayRequestService {

    final private Map<String, Runway> runwayMap;
    final private Map<String, List<FlightTrackingCallbackHandler>> callbackHandlers;
    final private ExecutorService executor;
    final private ReadWriteLock runwayLock;
    final private ReadWriteLock handlersLock;

    static final private long LOCK_TIMEOUT = 10L;
    static final private TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;
    static final private int LOCK_RETRIES = 6;

    public Servant() {
        runwayMap = new HashMap<>();
        callbackHandlers = new HashMap<>();
        executor = Executors.newCachedThreadPool();
        runwayLock = new ReentrantReadWriteLock(true);
        handlersLock = new ReentrantReadWriteLock(true);
    }

    private <V> V tryLockWithTimeout(final Callable<V> callable, final Lock lock)
            throws RemoteException, RunwayAlreadyExistsException {
        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (lock.tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    return callable.call();
                }
            }
        } catch (RemoteException | RunwayAlreadyExistsException | NoSuchRunwayException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerError("Unknown error", new Error(new IllegalMonitorStateException()));
        } finally {
            lock.unlock();
        }
        throw new ServerError("Exceeded lock retries", new Error(new IllegalMonitorStateException()));
    }

    @Override
    public void addRunway(final String name, final RunwayCategory category)
            throws RemoteException, RunwayAlreadyExistsException {
        tryLockWithTimeout(
                () -> {
                    if (runwayMap.containsKey(name))
                        throw new RunwayAlreadyExistsException();
                    runwayMap.put(name, new Runway(name, category));
                    return null;
                },
                runwayLock.writeLock()
        );
    }

    @Override
    public boolean isRunwayOpen(final String runwayName)
            throws RemoteException, NoSuchRunwayException {
        return tryLockWithTimeout(
                () -> Optional.ofNullable(runwayMap.get(runwayName))
                        .map(Runway::isOpen).orElseThrow(NoSuchRunwayException::new),
                runwayLock.readLock()
        );
    }

    @Override
    public void openRunway(final String runwayName)
            throws RemoteException, NoSuchRunwayException, IllegalStateException {
        tryLockWithTimeout(
                () -> {
                    final Runway runway = Optional.ofNullable(runwayMap.get(runwayName))
                            .orElseThrow(NoSuchRunwayException::new);
                    if (runway.isOpen())
                        throw new IllegalStateException("Runway is already open");
                    runway.setOpen(true);
                    return null;
                },
                runwayLock.writeLock()
        );
    }

    @Override
    public void closeRunway(final String runwayName)
            throws RemoteException, NoSuchRunwayException, IllegalStateException {
        tryLockWithTimeout(
                () -> {
                    final Runway runway = Optional.ofNullable(runwayMap.get(runwayName))
                            .orElseThrow(NoSuchRunwayException::new);
                    if (!runway.isOpen())
                        throw new IllegalStateException("Runway is already closed");
                    runway.setOpen(false);
                    return null;
                },
                runwayLock.writeLock()
        );
    }

    @Override
    public void issueDeparture() throws RemoteException {
        tryLockWithTimeout(() -> {
            for (Runway runway : runwayMap.values()) {
                if (runway.isOpen() && !runway.getDepartureQueue().isEmpty()) {
                    Flight departureFlight = runway.getDepartureQueue().poll();
                    departureFlight.setDepartedOn(LocalDateTime.now());

                    tryLockWithTimeout(() -> {
                        Optional.ofNullable(callbackHandlers.get(departureFlight.getId()))
                                .ifPresent(handlers -> {
                                    handlers.forEach(handler -> executor.submit(() ->
                                    {
                                        try {
                                            handler.onDeparture(
                                                    departureFlight.getId(),
                                                    departureFlight.getDestinationAirportId(),
                                                    runway.getName());

                                            // TODO: verificar
                                            // eliminamos el objeto luego del despeje
                                            handler.endProcess();
                                        } catch (RemoteException e) {
                                            // TODO: Manejar excepcion bien
                                            e.printStackTrace();
                                        }
                                    }));
                                    callbackHandlers.remove(departureFlight.getId());
                                });
                        return null;
                    }, handlersLock.writeLock());

                    runway.addToHistory(departureFlight);

                    for (Flight flight : runway.getDepartureQueue()) {
                        flight.incrementFlightsBeforeDeparture();
                        tryLockWithTimeout(() -> {
                            Optional.ofNullable(callbackHandlers.get(flight.getId()))
                                    .ifPresent(handlers -> handlers
                                            .forEach(handler -> executor.submit(() -> {
                                                try {
                                                    handler.onQueuePositionUpdate(
                                                            flight.getId(),
                                                            flight.getDestinationAirportId(),
                                                            runway.getName(),
                                                            runway.getFlightsAhead(flight.getId()));
                                                } catch (RemoteException e) {
                                                    // TODO: Manejar excepcion bien
                                                    e.printStackTrace();
                                                }
                                            })));
                            return null;
                        }, handlersLock.readLock());
                    }

                }
            }
            return null;
        }, runwayLock.writeLock());
    }

    @Override
    public ReassignmentLog rearrangeDepartures() throws RemoteException {
        List<Flight> flights = new ArrayList<>();

        tryLockWithTimeout(() -> {
                    runwayMap.values().forEach(runway -> {
                        flights.addAll(new ArrayList<>(runway.getDepartureQueue()));
                        runway.getDepartureQueue().clear();
                    });
                    return null;
                }, runwayLock.writeLock()
        );

        final ReassignmentLog log = new ReassignmentLog();
        for (Flight flight : flights) {
            try {
                requestRunway(flight);
                log.incrementAssigned();
            } catch (NoSuchRunwayException e) {
                tryLockWithTimeout(() -> {
                    callbackHandlers.get(flight.getId()).forEach((handler) -> {
                        try {
                            handler.endProcess();
                        } catch (RemoteException ex) {
                            // TODO: Manejar excepcion bien
                            ex.printStackTrace();
                        }
                    });
                    callbackHandlers.remove(flight.getId());
                    return null;
                }, handlersLock.readLock());
                log.addToFailed(flight.getId());
            }
        }
        return log;
    }

    @Override
    public void subscribe(final String flightId, final String airlineName, final FlightTrackingCallbackHandler handler)
            throws RemoteException, NoSuchFlightException {
        tryLockWithTimeout(() -> {
            // TODO: Excepcion distinta para cuando no matchea la aerolinea ?
            // TODO: Excepcion distinta para cuando el vuelo EXISTE en el history pero no en la queue (no esta esperando a despegar) ?
            // la idea de esta linea es evaluar si el vuelo existe, y si pertenece a la aerolinea
            runwayMap.values().stream()
                    .flatMap(runway -> runway.getDepartureQueue().stream())
                    .filter(f -> f.getId().equals(flightId) && f.getAirline().equals(airlineName))
                    .findFirst()
                    .orElseThrow(NoSuchFlightException::new);
            return null;
        }, runwayLock.readLock());

        tryLockWithTimeout(() -> {
            List<FlightTrackingCallbackHandler> handlers = callbackHandlers.computeIfAbsent(flightId, k -> new LinkedList<>());
            handlers.add(handler);
            return null;
        }, handlersLock.writeLock());
    }

    @Override
    public void requestRunway(final String flightId, final String destinationAirportId, final String airlineName,
                              final RunwayCategory minimumCategory) throws RemoteException, NoSuchRunwayException {
        requestRunway(new Flight(flightId, destinationAirportId, airlineName, minimumCategory));
    }

    private void requestRunway(final Flight flight)
            throws RemoteException, NoSuchRunwayException {
        Runway runway = tryLockWithTimeout(() -> {
            Runway answer = runwayMap.values().stream()
                    .filter(r -> r.getCategory().compareTo(flight.getCategory()) >= 0 && r.isOpen())
                    .min(Comparator.comparing(Runway::getDepartureQueueSize).thenComparing(Runway::getCategory)
                            .thenComparing(Runway::getName)).orElseThrow(NoSuchRunwayException::new);
            answer.addToQueue(flight);
            return answer;
        }, runwayLock.writeLock());

        tryLockWithTimeout(() -> {
            Optional.ofNullable(callbackHandlers.get(flight.getId()))
                    .ifPresent(handlers -> handlers
                            .forEach(handler -> executor.submit(() -> {
                                try {
                                    handler.onRunwayAssignment(flight.getId(), flight.getDestinationAirportId(),
                                            runway.getName(), runway.getFlightsAhead(flight.getId()));
                                } catch (RemoteException e) {
                                    // TODO: Manejar excepcion bien
                                    e.printStackTrace();
                                }
                            })));
            return null;
        }, handlersLock.readLock());
    }

    @Override
    public List<DepartureData> getAllDepartures() throws RemoteException {
        return tryLockWithTimeout(() -> runwayMap.values().stream()
                        .flatMap(runway -> runway.getDepartureHistory().stream()
                                .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                        runway.getName(),
                                        flight.getId(),
                                        flight.getDestinationAirportId(),
                                        flight.getAirline(),
                                        flight.getDepartedOn())))
                        .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                        .collect(Collectors.toList()),
                runwayLock.readLock());
    }

    @Override
    public List<DepartureData> getRunwayDepartures(final String runwayName) throws RemoteException, NoSuchRunwayException {

        return tryLockWithTimeout(() -> Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new)
                        .getDepartureHistory().stream()
                        .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                runwayName,
                                flight.getId(),
                                flight.getDestinationAirportId(),
                                flight.getAirline(),
                                flight.getDepartedOn()))
                        .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                        .collect(Collectors.toList()),
                runwayLock.readLock());
    }

    @Override
    public List<DepartureData> getAirlineDepartures(final String airline) throws RemoteException {

        return tryLockWithTimeout(() -> runwayMap.values().stream()
                        .flatMap(runway -> runway.getDepartureHistory().stream()
                                .filter(flight -> flight.getAirline().equals(airline))
                                .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                        runway.getName(),
                                        flight.getId(),
                                        flight.getDestinationAirportId(),
                                        flight.getAirline(),
                                        flight.getDepartedOn())))
                        .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                        .collect(Collectors.toList()),
                runwayLock.readLock());
    }
}
