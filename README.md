# skillBoard

## To run the Skill Board
    cd ~/skillBoard
    clojure -M:run

Be patient.  

## To exit the program
Hit the escape key several times and then wait 30s.  The program will shut down.

## Theory of Operation
The system polls FSP, Aviation Weather, and Radarcape once every 30 seconds or so. 

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
If no reservation for that tail number is checked out then the ADSB data will show as `UNRSV`
in the remarks.  

GPS altitude is displayed in 100s of feet.  If the ADSB transmitter is in "ground" mode,
then Altitude will be displayed as `GND`.

The ADSB position of the aircraft is compared against the list of geofences in the 
`src/skillBoard/config.cljc` file (see below).  If a match is found it is shown in the
remarks field.

### REMARKS
 * `NEAR` The aircraft is within 6NM of the airport.
 * `LOW` The aircraft is within 2NM of the airport and below 500' AGL.
 * `PATN` The aircraft is within 2NM of the airport and is within 500' of pattern altitude.
 * `TAXI` The aircraft is within 2NM of the airport, is in ground mode, or is below 30' AGL, 
and has a ground speed between 2 and 25kts.
 * `RAMP` The aircraft is within 2NM of the airport, is in ground mode, or is below 30' AGL,
and has a ground speed less than 2kts.


## To Update to a new version.
    git reset --hard
    git pull

Edit `~/deps.edn` to change the version number of `quil/quil` to `"3.1.0"`.  This is necessary
because the runtime environment on the mac mini is incompatible with the runtime environment of
the development system.

## Configuration
There are two files that configure the system.  

### `~/private/config`
This file holds information that should be kept secure.  The format is:

    {
    :fsp-key "<the fsp key>"
    :fsp-operator-id "<the fsp operator id>"
    }

### `~/src/skillBoard/config.cljc`
This file holds information that describes the local environment.
The format is as follows and ought to be self explanatory

    (ns skillBoard.config)
    
    (def config (atom nil))
    
    (defn load-config []
      (reset! config (read-string (slurp "private/config"))))
    
    ;-- System Configuration
    
    ;Display configuration
    (def cols 63) ;The number of columns in the display
    (def flights 18) ; The number of flights to display
    
    ;Home airport configuration
    (def airport "KUGN")
    (def airport-lat-lon [42.4221486 -87.8679161])
    (def time-zone "America/Chicago")
    (def airport-elevation 728.1)
    (def pattern-altitude 1728)
    
    ;Wider area configuration.  
    ; 
    ;Named geofences each of which describes a cylinder of airspace. 
    ;a flight in one of those cylinders will show the name in the remarks. 
    (def geofences [{:name "KUGN"
                     :lat 42.4221486
                     :lon -87.8679161
                     :radius 4
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "KENW"
                     :lat 42.5960633
                     :lon -87.9273236
                     :radius 4
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "C89"
                     :lat 42.7032500
                     :lon -87.9589722
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "57C"
                     :lat 42.7971667
                     :lon -88.3726111
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "KBUU"
                     :lat 42.6907171
                     :lon -88.3046825
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "10C"
                     :lat 42.4028889
                     :lon -88.3751111
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "C81"
                     :lat 42.3246111
                     :lon -88.0740881
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
                    {:name "3CK"
                     :lat 42.2068611
                     :lon -88.3226944
                     :radius 3
                     :min-alt 1000
                     :max-alt 3200}
    
                    {:name "PRAC"
                     :lat 42.54
                     :lon -88.49
                     :radius 8
                     :min-alt 1000
                     :max-alt 7000}
                    ])
    
    
    

   


