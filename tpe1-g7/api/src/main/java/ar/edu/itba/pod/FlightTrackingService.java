package ar.edu.itba.pod;

import ar.edu.itba.pod.exceptions.NoSuchFlightException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FlightTrackingService extends Remote {

    void subscribe(final String flightId, final String airlineName, final FlightTrackingCallbackHandler handler) throws RemoteException, NoSuchFlightException, InterruptedException;
}
