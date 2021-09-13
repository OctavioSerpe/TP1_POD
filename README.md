# Trabajo Práctico Especial 1: Despegues

## Programación de Objetos Distribuidos - 2º cuatrimestre 2021

---

## Autores

- [Serpe, Octavio Javier](https://github.com/OctavioSerpe) - Legajo 60076
- [Rodríguez, Manuel Joaquín](https://github.com/rodriguezmanueljoaquin) - Legajo 60258
- [Arca, Gonzalo](https://github.com/gonzaloarca) - Legajo 60303
- [Parma, Manuel Félix](https://github.com/manuelfparma) - Legajo 60425

---

## Dependencias

- Java 8 (JDK 8)
- Maven

---

## Cómo compilar el proyecto

Desde la línea de comando, situado dentro de la carpeta `tpe1-g7`, ejecutar el comando:

```bash
$ mvn clean install
```

Luego, para descomprimir los `.jar` generados y otorgar permisos de ejecución, lectura, y escritura a todos los ejecutables obtenidos, ejecutar situado desde la carpeta `tpe1-g7`:

```bash
$ ./pod-chmod-run.sh
```

---

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

### 3. Clients

Finalmente, para ejecutar los _clients_ situarse en la carpeta `tpe1-g7/client/target/tpe1-g7-client-1.0-SNAPSHOT` y ejecutar el comando:
