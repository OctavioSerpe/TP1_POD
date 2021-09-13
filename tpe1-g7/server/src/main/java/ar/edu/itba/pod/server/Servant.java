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
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Servant implements ManagementService, DepartureQueryService, FlightTrackingService, RunwayRequestService {

    // TODO: thread-safe

    final private Map<String, Runway> runwayMap;
    final private Map<String, List<FlightTrackingCallbackHandler>> callbackHandlers;
    final private ExecutorService executor;
    final private ReadWriteLock runwayLock;
    final private ReadWriteLock handlersLock;

    static final private long LOCK_TIMEOUT = 10L;
    static final private TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;
    static final private int LOCK_RETRIES = 6;

    static final private String ERROR_EXCEDEED_LOCK_RETRIES = "Exceeded lock retries";
    static final private String ERROR_THREAD_INTERRUPTED = "Thread interrupted";

    public Servant() {
        runwayMap = new HashMap<>();
        callbackHandlers = new HashMap<>();
        executor = Executors.newCachedThreadPool();
        runwayLock = new ReentrantReadWriteLock(true);
        handlersLock = new ReentrantReadWriteLock(true);
    }

    @Override
    public void addRunway(final String name, final RunwayCategory category)
            throws RemoteException, RunwayAlreadyExistsException {
//        final Callable<Void> callable = () -> {
//            if (runwayMap.containsKey(name))
//                throw new RunwayAlreadyExistsException();
//            runwayMap.put(name, new Runway(name, category));
//            return null;
//        };

        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    if (runwayMap.containsKey(name))
                        throw new RunwayAlreadyExistsException();
                    runwayMap.put(name, new Runway(name, category));
                    return;
                }
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isRunwayOpen(final String runwayName) throws RemoteException, NoSuchRunwayException {
        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    return Optional.ofNullable(runwayMap.get(runwayName)).map(Runway::isOpen).orElseThrow(NoSuchRunwayException::new);
                }
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.readLock().unlock();
        }
    }

//    private <V> V tryLockWithTimeout(final Callable<V> callable, final Lock lock) throws Exception{
//        try {
//            for (int i = 0; i < LOCK_RETRIES; ++i) {
//                if (lock.tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
//                    return callable.call();
//                }
//            }
//            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
//        } finally {
//            lock.unlock();
//        }
//    }

    @Override
    public void openRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
                    if (runway.isOpen())
                        throw new IllegalStateException("Runway is already open");
                    runway.setOpen(true);
                    return;
                }
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }
    }

    @Override
    public void closeRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException {
        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    final Runway runway = Optional.ofNullable(runwayMap.get(runwayName)).orElseThrow(NoSuchRunwayException::new);
                    if (!runway.isOpen())
                        throw new IllegalStateException("Runway is already closed");
                    runway.setOpen(false);
                    return;
                }
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }
    }

    @Override
    public void issueDeparture() throws RemoteException {
        try {
            for (int i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    runwayMap.forEach((runwayName, runway) -> {
                        // obtener lock de runway
                        try {
                            int j;
                            for (j = 0; j < LOCK_RETRIES; ++j) {
                                if (handlersLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {

                                    // TODO: lock sobre handlers!!!!!!! (el problema es el codigo estilo html anidado)
                                    if (runway.isOpen() && !runway.getDepartureQueue().isEmpty()) {
                                        Flight departureFlight = runway.getDepartureQueue().poll();
                                        departureFlight.setDepartedOn(LocalDateTime.now());

                                        Optional.ofNullable(callbackHandlers.get(departureFlight.getId()))
                                                .ifPresent(handlers -> {
                                                    handlers.forEach(handler -> executor.submit(() ->
                                                    {
                                                        try {
                                                            handler.onDeparture(
                                                                    departureFlight.getId(),
                                                                    departureFlight.getDestinationAirportId(),
                                                                    runwayName);
                                                        } catch (RemoteException e) {
                                                            // TODO: Manejar excepcion bien
                                                            e.printStackTrace();
                                                        }
                                                    }));
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
                                                                handler.onQueuePositionUpdate(
                                                                        flight.getId(),
                                                                        flight.getDestinationAirportId(),
                                                                        runwayName,
                                                                        runway.getFlightsAhead(flight.getId()));
                                                            } catch (RemoteException e) {
                                                                // TODO: Manejar excepcion bien
                                                                e.printStackTrace();
                                                            }
                                                        })));
                                    });
                                    break;
                                }
                            }
                            if (j == LOCK_RETRIES)
                                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
                        } catch (InterruptedException | ServerError e) {
                            // throw new ServerError("Server", new Error("Thread interrupted"));
                            e.printStackTrace();
                        } finally {
                            handlersLock.writeLock().unlock();
                        }
                    });
                    return;
                }
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }

    }

    @Override
    public ReassignmentLog rearrangeDepartures() throws RemoteException {
        List<Flight> flights = new ArrayList<>();

        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    runwayMap.values().forEach(runway -> {
                        flights.addAll(new ArrayList<>(runway.getDepartureQueue()));
                        runway.getDepartureQueue().clear();
                    });
                    break;
                }
            }
            if (i == LOCK_RETRIES)
                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }

        final ReassignmentLog log = new ReassignmentLog();
        flights.forEach(flight ->
        {
            try {
                requestRunway(flight);
                log.incrementAssigned();
            } catch (RemoteException e) {
                e.printStackTrace(); //TODO ????? LO HACE OCTA
            } catch (NoSuchRunwayException e) {
                callbackHandlers.get(flight.getId()).forEach((handler) -> {
                    try {
                        handler.endProcess();
                    } catch (RemoteException remoteExceptionHandler) {
                        remoteExceptionHandler.printStackTrace(); //TODO ????? LO HACE OCTA
                    }
                });
                callbackHandlers.remove(flight.getId());
                log.addToFailed(flight.getId());
            }
        });
        return log;
    }

    @Override
    public void subscribe(final String flightId, final String airlineName, final FlightTrackingCallbackHandler handler)
            throws RemoteException, NoSuchFlightException {
        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    // TODO: Excepcion distinta para cuando no matchea la aerolinea ?
                    // TODO: Excepcion distinta para cuando el vuelo EXISTE en el history pero no en la queue (no esta esperando a despegar) ?
                    // la idea de esta linea es evaluar si el vuelo existe, y si pertenece a la aerolinea
                    runwayMap.values().stream()
                            .flatMap(runway -> runway.getDepartureQueue().stream())
                            .filter(f -> f.getId().equals(flightId) && f.getAirline().equals(airlineName))
                            .findFirst()
                            .orElseThrow(NoSuchFlightException::new);
                    break;
                }
            }
            if (i == LOCK_RETRIES)
                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.readLock().unlock();
        }

        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (handlersLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    List<FlightTrackingCallbackHandler> handlers = callbackHandlers.computeIfAbsent(flightId, k -> new LinkedList<>());
                    handlers.add(handler);
                    return;
                }
            }
            if (i == LOCK_RETRIES)
                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            handlersLock.writeLock().unlock();
        }
    }

    @Override
    public void requestRunway(final String flightId, final String destinationAirportId, final String airlineName,
                              final RunwayCategory minimumCategory) throws RemoteException, NoSuchRunwayException {
        requestRunway(new Flight(flightId, destinationAirportId, airlineName, minimumCategory));
    }

    private void requestRunway(final Flight flight)
            throws RemoteException, NoSuchRunwayException {

        Optional<Runway> maybeRunway = Optional.empty();
        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.writeLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    maybeRunway = runwayMap.values().stream()
                            .filter(r -> r.getCategory().compareTo(flight.getCategory()) >= 0 && r.isOpen())
                            .min(Comparator.comparing(Runway::getDepartureQueueSize).thenComparing(Runway::getCategory)
                                    .thenComparing(Runway::getName));
                    maybeRunway.orElseThrow(NoSuchRunwayException::new).addToQueue(flight);
                    break;
                }
            }
            if (i == LOCK_RETRIES)
                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.writeLock().unlock();
        }

        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (handlersLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
                    final Runway runway = maybeRunway.orElseThrow(NoSuchRunwayException::new);
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
                    return;
                }
            }
            if (i == LOCK_RETRIES)
                throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            handlersLock.readLock().unlock();
        }
    }

    @Override
    public List<DepartureData> getAllDepartures() throws RemoteException {
        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
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
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.readLock().unlock();
        }
    }

    @Override
    public List<DepartureData> getRunwayDepartures(final String runwayName) throws RemoteException, NoSuchRunwayException {
        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
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
            }
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.readLock().unlock();
        }
    }

    @Override
    public List<DepartureData> getAirlineDepartures(final String airline) throws RemoteException {
        try {
            int i;
            for (i = 0; i < LOCK_RETRIES; ++i) {
                if (runwayLock.readLock().tryLock(LOCK_TIMEOUT, LOCK_TIME_UNIT)) {
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
            throw new ServerError(ERROR_EXCEDEED_LOCK_RETRIES, new Error(new IllegalMonitorStateException()));
        } catch (InterruptedException e) {
            throw new ServerError(ERROR_THREAD_INTERRUPTED, new Error(e));
        } finally {
            runwayLock.readLock().unlock();
        }
    }
}
