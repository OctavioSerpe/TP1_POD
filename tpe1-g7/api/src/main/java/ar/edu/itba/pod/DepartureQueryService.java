package ar.edu.itba.pod;

import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.models.DepartureData;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface DepartureQueryService extends Remote {

    List<DepartureData> getAllDepartures() throws RemoteException;

    List<DepartureData> getRunwayDepartures(final String runwayName) throws RemoteException, NoSuchRunwayException;

    List<DepartureData> getAirlineDepartures(final String airline) throws RemoteException;
}
