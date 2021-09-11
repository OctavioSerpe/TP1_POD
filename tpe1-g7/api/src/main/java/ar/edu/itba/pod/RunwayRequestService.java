package ar.edu.itba.pod;

import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.models.RunwayCategory;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RunwayRequestService extends Remote {

    void requestRunway(final String flightId,
                       final String destinationAirportId,
                       final String airlineName,
                       final RunwayCategory minimumCategory) throws RemoteException, NoSuchRunwayException;

}
