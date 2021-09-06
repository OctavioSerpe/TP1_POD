package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RunwayRequestService extends Remote {

    // TODO: pasar category a enum, custom error en caso de no encontrar una pista
    void requestRunway(final String flightId, final String airportId, final String airlineName, final String minimumCategory) throws RemoteException;
}
