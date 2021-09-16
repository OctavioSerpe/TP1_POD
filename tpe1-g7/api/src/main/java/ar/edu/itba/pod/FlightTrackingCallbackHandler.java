package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * Interfaz utilizada para registrar los callbacks del cliente
 */
public interface FlightTrackingCallbackHandler extends Remote {

    /*
     *  Callback cuando el vuelo fue asignado a una pista
     */
    void onRunwayAssignment(final String flightId, final String destination, final String runway, final long flightsAhead)
            throws RemoteException;

    /*
     *  Callback cuando el vuelo cambió su posicion en la cola de espera de la pista
     */
    void onQueuePositionUpdate(final String flightId, final String destination, final String runway, final long flightsAhead)
            throws RemoteException;

    /*
     *  Callback cuando el vuelo despego
     */
    void onDeparture(final String flightId, final String destination, final String runway)
            throws RemoteException;

    /*
     *  Callback para eliminar el handler de callbacks
     */
    void endProcess()
            throws RemoteException;
}
