package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;
/*
    Interface used by FlightTrackingService to execute methods on an update in the status of one specific flight.
 */
public interface FlightTrackingCallbackHandler extends Remote {

    /*
        Method executed when the flight gets a runway assigned.
     */
    void onRunwayAssignment(final String flightId, final String destination, final String runway, final long flightsAhead)
            throws RemoteException;

    /*
        Method executed when the flight gets its position in the queue of runway updated.
     */
    void onQueuePositionUpdate(final String flightId, final String destination, final String runway, final long flightsAhead)
            throws RemoteException;

    /*
        Method executed when the flight gets a runway assigned.
     */
    void onDeparture(final String flightId, final String destination, final String runway)
            throws RemoteException;

    /*
        Method executed when the flight has to be ended.
     */
    void endProcess()
            throws RemoteException;
}
