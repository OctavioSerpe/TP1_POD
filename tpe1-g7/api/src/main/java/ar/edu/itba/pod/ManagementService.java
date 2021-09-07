package ar.edu.itba.pod;

import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.RunwayCategory;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ManagementService extends Remote {

    void addRunway(final String name, final RunwayCategory category) throws RemoteException, RunwayAlreadyExistsException;

    boolean isRunwayOpen(final String runwayName) throws RemoteException, NoSuchRunwayException;

    void openRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException;

    void closeRunway(final String runwayName) throws RemoteException, NoSuchRunwayException, IllegalStateException;

    void issueDeparture() throws RemoteException;

    void rearrangeDepartures() throws RemoteException;
}
