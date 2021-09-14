# Trabajo Práctico Especial 1: Despegues

## 72.42 Programación de Objetos Distribuidos - 2º cuatrimestre 2021

### Instituto Tecnológico de Buenos Aires

## Autores

- [Serpe, Octavio Javier](https://github.com/OctavioSerpe) - Legajo 60076
- [Rodríguez, Manuel Joaquín](https://github.com/rodriguezmanueljoaquin) - Legajo 60258
- [Arca, Gonzalo](https://github.com/gonzaloarca) - Legajo 60303
- [Parma, Manuel Félix](https://github.com/manuelfparma) - Legajo 60425

## Tabla de contenidos

- [Dependencias](#dependencias)
- [Cómo compilar el proyecto](#cómo-compilar-el-proyecto)
- [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
  - [1. Registry](#1-registry)
  - [2. Server](#2-server)
  - [3. Client](#3-client)
    - [3.1. Cliente de Administración](#31-cliente-de-administración)
    - [3.2. Cliente de Solicitud de Pista](#32-cliente-de-solicitud-de-pista)
    - [3.3. Cliente de Seguimiento de Vuelo](#33-cliente-de-seguimiento-de-vuelo)
    - [3.4. Cliente de Consulta](#34-cliente-de-consulta)

## Dependencias

- Java 8 (JDK 8)
- Maven

## Cómo compilar el proyecto

Desde la línea de comando, situado dentro de la carpeta `tpe1-g7`, ejecutar el comando:

```bash
$ mvn clean install
```

Luego, para descomprimir los `.jar` generados y otorgar permisos de ejecución, lectura, y escritura a todos los ejecutables obtenidos, ejecutar situado desde la carpeta `tpe1-g7`:

```bash
$ ./pod-chmod-run.sh
```

## Cómo ejecutar el proyecto

### 1. Registry

Primero, para ejecutar el _registry_ situarse en la carpeta `tpe1-g7/server/target/tpe1-g7-server-1.0-SNAPSHOT` y ejecutar el comando:

```bash
$ ./run-registry.sh
```

> IMPORTANTE: Asegurése de no tener ningún proceso escuchando en el puerto 1099

### 2. Server

Luego, para ejecutar el _server_ situarse nuevamente en la carpeta `tpe1-g7/server/target/tpe1-g7-server-1.0-SNAPSHOT` y ejecutar el comando:

```bash
$ ./run-server.sh
```

### 3. Clientes

#### 3.1. Cliente de Administración

Para ejecutar el cliente de administración situarse en la carpeta `tpe1-g7/client/target/tpe1-g7-client-1.0-SNAPSHOT` y ejecutar el comando:

```bash
$ ./run-management.sh -DserverAddress=xx.xx.xx.xx:yyyy -Daction=actionName [ -Drunway=runwayName | -Dcategory=minCategory ]
```

donde

- `xx.xx.xx.xx:yyyy` es la dirección IP y el puerto donde está publicado el servicio de
  administración de los despegues.
- `actionName` es el nombre de la acción a realizar.
  - `add`: Agrega una pista de categoría `minCategory` con el nombre `runwayName`.
    Deberá imprimir en pantalla el estado de la pista luego de agregarla o el error
    correspondiente.
  - `open`: Abre la pista `runwayName`. Deberá imprimir en pantalla el estado de la pista
    luego de invocar a la acción o el error correspondiente.
  - `close`: Cierra la pista `runwayName`. Deberá imprimir en pantalla el estado de la pista
    luego de invocar a la acción o el error correspondiente.
  - `status`: Consulta el estado de la pista `runwayName`. Deberá imprimir en pantalla el
    estado de la pista al momento de la consulta.
  - `takeOff`: Emite una orden de despegue en las pistas abiertas. Deberá imprimir en
    pantalla la finalización de la acción.
  - `reorder`: Emite una orden de reordenamiento en las pistas. Deberá imprimir en
    pantalla la cantidad de vuelos que obtuvieron una pista y detallar aquellos que no.

#### 3.2. Cliente de Solicitud de Pista

Para ejecutar el cliente de solicitud de pista situarse en la carpeta `tpe1-g7/client/target/tpe1-g7-client-1.0-SNAPSHOT` y ejecutar el comando:

```bash
$ ./run-runway.sh -DserverAddress=xx.xx.xx.xx:yyyy -DinPath=fileName
```

donde

- `xx.xx.xx.xx:yyyy` es la dirección IP y el puerto donde está publicado el servicio de
  solicitud de pista.
- `fileName` es el path del archivo de entrada con las solicitudes de pista

#### 3.3. Cliente de Seguimiento de Vuelo

```bash
$ ./run-airline.sh -DserverAddress=xx.xx.xx.xx:yyyy -Dairline=airlineName
-DflightCode=flightCode
```

donde

- `xx.xx.xx.xx:yyyy` es la dirección IP y el puerto donde está publicado el servicio de
  seguimiento de vuelo.
- `airlineName`: el nombre de la aerolínea
- `flightCode`: el código identificador de un vuelo de la aerolínea airlineName que esté
  esperando despegar.

#### 3.4. Cliente de Consulta

Para ejecutar el cliente de consulta situarse en la carpeta `tpe1-g7/client/target/tpe1-g7-client-1.0-SNAPSHOT` y ejecutar el comando:

```bash
 ./run-query.sh -DserverAddress=xx.xx.xx.xx:yyyy [ -Dairline=airlineName |
-Drunway=runwayName ] -DoutPath=fileName
```

donde

- `xx.xx.xx.xx:yyyy` es la dirección IP y el puerto donde está publicado el servicio de
  consulta de los despegues.
- Si no se indica `-Dairline` ni `-Drunway` se resuelve la consulta 1.
- Si se indica `-Dairline`, `airlineName` es el nombre de la aerolínea elegida para resolver
  la consulta 2.
- Si se indica `-Drunway`, `runwayName` es el nombre de la pista elegida para resolver la
  consulta 3.
