package ar.edu.itba.pod;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ManagementService extends Remote {
    
    // TODO: pasar category a enum, arrojar custom error si ya existe la pista
    void addRunway(final String name, final String category) throws RemoteException;

    // TODO: custom error si no existe la pista
    boolean isRunwayOpen(final String runway) throws RemoteException;

    // TODO: custom error si no existe la pista o se ordena abrir una pista ya abierta
    void openRunway(final String runway) throws RemoteException;

    // TODO: custom error si no existe la pista o se ordena cerrar una pista ya cerrada
    void closeRunway(final String runway) throws RemoteException;

    void issueDeparture() throws RemoteException;

    void rearrangeDepartures() throws RemoteException;
}
