package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FlightTrackingService extends Remote {

    // TODO: custom error cuando Se quiera registrar para un vuelo que no pertenezca a la aerolínea y Si el vuelo no está esperando a despegar
    void subscribe(final FlightTrackingCallbackHandler callbackHandler) throws RemoteException;
}
