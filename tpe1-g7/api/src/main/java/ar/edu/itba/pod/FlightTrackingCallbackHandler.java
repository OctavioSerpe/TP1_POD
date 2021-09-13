package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FlightTrackingCallbackHandler extends Remote {

    void onRunwayAssignment(final String flightId, final String destination, final String runway, final long flightsAhead) throws RemoteException;

    void onQueuePositionUpdate(final String flightId, final String destination, final String runway, final long flightsAhead) throws RemoteException;

    void onDeparture(final String flightId, final String destination, final String runway) throws RemoteException;

    void endProcess() throws RemoteException;
}
