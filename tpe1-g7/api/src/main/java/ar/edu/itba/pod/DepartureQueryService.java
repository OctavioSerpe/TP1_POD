package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface DepartureQueryService extends Remote {

    // TODO: custom class para los despegues y enum queryType
    List<String> getDepartures(final String queryType) throws RemoteException;
}
