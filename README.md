# SkillBoard

SkillBoard is a Clojure-based real-time aviation display system designed for flight training
facilities. It runs as a continuous display, designed for a large wall screen, providing instructors and students with
situational awareness for flight operations.  It integrates data from multiple sources including FlightSchedulePro for
reservation management, Aviation Weather for meteorological information, and Radarcape for ADSB
aircraft tracking in order to display current flight
schedules, weather conditions, flight categories, and nearby traffic on a cycling multi-screen
interface.

## Theory of Operation
The system polls FSP, Aviation Weather, and Radarcape once every 60 seconds or so
as specified in `config/seconds-between-internet-polls`. 

If FSP communication fails for some reason (which it does) the system will remember
the last batch of data it got.

If Radarcape communications fail the respective fields on the screen will go blank until
communication is restored.

If Aviation Weather communications fail the screen will show "NO METAR" until communication
is restored.

Reservations for the past six hours and until tomorrow are displayed on the screen in
chronological order.  Reservations that have been checked in are removed.

ADSB data from Radarcape are collected for every tail number listed as "Active" in FSP.  
The ADSB data are associated, by tail number, with checked out reservations.
If no reservation for that tail number is checked out then the ADSB data will show as `NO-CO`
in the remarks.  

GPS altitude is displayed in 100s of feet.  If the ADSB transmitter is in "ground" mode,
then Altitude will be displayed as `GND`.

The ADSB position of the aircraft is compared against the list of geofences in the 
`src/skillBoard/config.clj` file (see below).  If a match is found it is shown in the
remarks field.

### REMARKS
 * `NEAR` The aircraft is within 6NM of the airport.
 * `LOW` The aircraft is within 2NM of the airport and below 500' AGL.
 * `PATN` The aircraft is within 2NM of the airport and is within 500' of pattern altitude.
 * `TAXI` The aircraft is within 2NM of the airport, is in ground mode, or is below 30' AGL, 
and has a ground speed between 2 and 25kts.
 * `RAMP` The aircraft is withgit sin 2NM of the airport, is in ground mode, or is below 30' AGL,
and has a ground speed less than 2kts.

### Status Lights
The three status lights next to the clock on the upper right of the screen correspond to
the three internet data sources.  Top is FlightSchedulePro, middle is Radarcape, bottom is
Aviation Weather.  Green means data is being received.  Orange means one failed attempt.  
Red means two or more consecutive failures.

## Screens
The display cycles between four different screens as specified in `config/screens`.  
The four screens are:

### Flight Operations Screen
![screenshot](images/Flight%20Operations%20Screen.jpg)
   * Presents all the reserved flights for today and the past six hours.
     * Crew includes Instructor and Student names.
     * `BRG/ALT/GS` are the bearing, altitude (in 100s of feet), and ground speed from the Radarcape ADSB data.
        The bearing is in the form `XXXBBBDDD` where `XXX` is the airport from which the bearing is taken,
        `BBB` is the bearing from that airport in degrees, and `DDD` is the distance in NM from that airport.
     * Flights in white (`config/scheduled-flight-color`) are scheduled flights.
     * Flights in green (`config/on-ground-color`) are flights that are on the ground.
     * Flights in yellow (`config/unscheduled-flight-color`) are flights that are in the air but not checked out.
### Weather Screen
![screenshot](images/Weather%20Screen.jpg)     
   * Presents the TAF for `config/taf-airport` 
   * and the METAR history for `config/airport`.
### Flight Category Screen
![screenshot](images/Flight%20Category%20Screen.jpg)
   * Presents the flight category, clouds, visibility, wind, 
     and distance for the airports specified in `config/flight-category-airports`.
### Nearby Traffic Screen
![screenshot](images/Nearby%20Traffic%20Screen.jpg)
   * Presents the traffic in the region specified by `config/nearby-altitude-range` and `config/nearby-distance`.
     * Traffic in white (`config/in-fleet-color`) are flights of aircraft in the fleet.
     * Traffic in yellow (`config/out-of-fleet-color`) are flights of aircraft not in the fleet.
     * Traffic in green (`config/on-ground-color`) are on the ground.

Screens can be manually changed by hitting the space bar.

## To run the Skill Board
    cd ~/skillBoard
    clojure -M:run

Be patient.  

### To exit the program
Hit the escape key.

## To Update to a new version.
    git reset --hard
    git pull

## Configuration
There are two files that configure the system.  

### `private/config`
This file holds information that should be kept secure.  The format is:

    {
    :fsp-key "<the fsp key>"
    :fsp-operator-id "<the fsp operator id>"
    }

### `src/skillBoard/config.clj`
This file holds information that describes the local environment and the display behavior.



