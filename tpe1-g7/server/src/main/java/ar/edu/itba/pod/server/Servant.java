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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(Servant.class);

    final private Map<String, Runway> runwayMap;
    final private Map<String, List<FlightTrackingCallbackHandler>> callbackHandlers;
    final private ExecutorService executor;
    final private ReadWriteLock runwayLock;
    final private ReadWriteLock handlersLock;

    static final private long LOCK_TIMEOUT = 5L;
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
        } catch (RemoteException | RunwayAlreadyExistsException | NoSuchRunwayException |
                NoSuchFlightException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerError("Unknown error", new Error(e));
        } finally {
            lock.unlock();
        }
        throw new ServerError("Exceeded lock retries", new Error(new IllegalMonitorStateException()));
    }

    @Override
    public void addRunway(final String name, final RunwayCategory category)
            throws RemoteException, RunwayAlreadyExistsException {
        if (name == null || category == null)
            throw new IllegalArgumentException("Runway name and Runway category MUST NOT be null");

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
        if (runwayName == null)
            throw new IllegalArgumentException("Runway name MUST NOT be null");

        return tryLockWithTimeout(
                () -> Optional.ofNullable(runwayMap.get(runwayName))
                        .map(Runway::isOpen).orElseThrow(NoSuchRunwayException::new),
                runwayLock.readLock()
        );
    }

    @Override
    public void openRunway(final String runwayName)
            throws RemoteException, NoSuchRunwayException {
        if (runwayName == null)
            throw new IllegalArgumentException("Runway name MUST NOT be null");

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
            throws RemoteException, NoSuchRunwayException {
        if (runwayName == null)
            throw new IllegalArgumentException("Runway name MUST NOT be null");

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
                if (runway.isOpen() && !runway.isQueueEmpty()) {
                    Flight departureFlight = runway.pollFromQueue();
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
                                            handler.endProcess();
                                        } catch (RemoteException e) {
                                            logger.error("An unknown error has occurred.");
                                            logger.error(Arrays.toString(e.getStackTrace()));
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
                                                    logger.error("An unknown error has occurred.");
                                                    logger.error(Arrays.toString(e.getStackTrace()));
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
        final List<Flight> flights = new ArrayList<>();

        tryLockWithTimeout(() -> {
                    runwayMap.values().forEach(runway -> {
                        flights.addAll(new ArrayList<>(runway.getDepartureQueue()));
                        runway.clearQueue();
                    });
                    return null;
                }, runwayLock.writeLock()
        );

        long assignedCount = 0;
        final List<String> failed = new ArrayList<>();
        for (Flight flight : flights) {
            try {
                requestRunway(flight);
                assignedCount++;
            } catch (NoSuchRunwayException noSuchRunwayException) {
                tryLockWithTimeout(() -> {
                    Optional.ofNullable(callbackHandlers.get(flight.getId())).ifPresent((handlers) ->
                            handlers.forEach(handler -> executor.submit(() -> {
                                try {
                                    handler.endProcess();
                                } catch (RemoteException e) {
                                    logger.error("An unknown error has occurred.");
                                    logger.error(Arrays.toString(e.getStackTrace()));
                                }
                            })));
                    callbackHandlers.remove(flight.getId());
                    return null;
                }, handlersLock.writeLock());
                failed.add(flight.getId());
            }
        }
        return new ReassignmentLog(assignedCount, failed);
    }

    @Override
    public void subscribe(final String flightId, final String airlineName, final FlightTrackingCallbackHandler handler)
            throws RemoteException, NoSuchFlightException {

        if (flightId == null || airlineName == null || handler == null)
            throw new IllegalArgumentException("Runway name, airline name and handler MUST NOT be null");

        tryLockWithTimeout(() -> {
                    final RunwayAssignmentCallbackParameters callbackParams = new RunwayAssignmentCallbackParameters();
                    runwayMap.values().stream()
                            .filter(r -> r.getDepartureQueue().stream().anyMatch(f -> {
                                if (f.getId().equals(flightId) && f.getAirline().equals(airlineName)) {
                                    callbackParams.setFlightId(f.getId());
                                    callbackParams.setDestinationAirportId(f.getDestinationAirportId());
                                    callbackParams.setRunwayName(r.getName());
                                    callbackParams.setFlightsAhead(r.getFlightsAhead(f.getId()));
                                    return true;
                                }
                                return false;
                            })).findFirst().orElseThrow(NoSuchFlightException::new);

                    tryLockWithTimeout(() -> {
                        final List<FlightTrackingCallbackHandler> handlers = callbackHandlers
                                .computeIfAbsent(flightId, k -> new LinkedList<>());
                        handlers.add(handler);
                        handler.onRunwayAssignment(
                                callbackParams.getFlightId(),
                                callbackParams.getDestinationAirportId(),
                                callbackParams.getRunwayName(),
                                callbackParams.getFlightsAhead());
                        return null;
                    }, handlersLock.writeLock());

                    return null;
                },
                runwayLock.readLock());
    }

    @Override
    public void requestRunway(final String flightId, final String destinationAirportId, final String airlineName,
                              final RunwayCategory minimumCategory) throws RemoteException, NoSuchRunwayException {

        if (flightId == null || destinationAirportId == null || airlineName == null | minimumCategory == null)
            throw new IllegalArgumentException("flight ID, destination airport ID, airline name and minimum runway category MUST NOT be null");

        requestRunway(new Flight(flightId, destinationAirportId, airlineName, minimumCategory));
    }

    private void requestRunway(final Flight flight)
            throws RemoteException, NoSuchRunwayException {
        final Runway runway = tryLockWithTimeout(() -> {
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
                                    logger.error("An unknown error has occurred.");
                                    logger.error(Arrays.toString(e.getStackTrace()));
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
        if (runwayName == null)
            throw new IllegalArgumentException("Runway name MUST NOT be null");

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
        if (airline == null)
            throw new IllegalArgumentException("Airline MUST NOT be null");

        return tryLockWithTimeout(() -> runwayMap.values().stream()
                        .flatMap(runway -> runway.getDepartureHistory().stream()
                                .filter(flight -> flight.getAirline().equals(airline))
                                .map(flight -> new DepartureData(flight.getFlightsBeforeDeparture(),
                                        runway.getName(),
                                        flight.getId(),
                                        flight.getDestinationAirportId(),
                                        airline,
                                        flight.getDepartedOn())))
                        .sorted(Comparator.comparing(DepartureData::getDepartedOn))
                        .collect(Collectors.toList()),
                runwayLock.readLock());
    }

    private class RunwayAssignmentCallbackParameters {
        private long flightsAhead;
        private String flightId;
        private String destinationAirportId;
        private String runwayName;

        public RunwayAssignmentCallbackParameters() {
        }

        public void setFlightsAhead(long flightsAhead) {
            this.flightsAhead = flightsAhead;
        }

        public void setFlightId(String flightId) {
            this.flightId = flightId;
        }

        public void setDestinationAirportId(String destinationAirportId) {
            this.destinationAirportId = destinationAirportId;
        }

        public void setRunwayName(String runwayName) {
            this.runwayName = runwayName;
        }

        public long getFlightsAhead() {
            return flightsAhead;
        }

        public String getFlightId() {
            return flightId;
        }

        public String getDestinationAirportId() {
            return destinationAirportId;
        }

        public String getRunwayName() {
            return runwayName;
        }
    }
}
