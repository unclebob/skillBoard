Feature: Flight Operations screen
  The Flight Operations screen must show active training operations, ADS-B position, and unscheduled aircraft clearly.

  Background:
    Given the home airport is KUGN
    And pattern altitude is 1728 feet
    And the display has 64 columns

  Scenario: Active reservations are displayed in chronological order
    Given the current local time is 2025-12-03 16:00
    And FlightSchedulePro reservations include:
      | id | tail   | activity        | start_time       | checked_in | checked_out |
      | 1  | N111AA | Flight Training | 2025-12-03 15:00 |            |             |
      | 2  | N222BB | New Customer    | 2025-12-03 14:00 |            |             |
      | 3  | N333CC | Ground School   | 2025-12-03 15:30 |            |             |
      | 4  | N444DD | Flight Training | 2025-12-03 09:00 |            |             |
      | 5  | N555EE | Flight Training | 2025-12-03 15:15 | 15:55      |             |
    And no matching flight records are checked in
    When the Flight Operations screen is built
    Then the screen lists reservations 2 and 1 in that order
    And it does not list reservations 3, 4, or 5

  Scenario: A checked-out reservation is associated with matching ADS-B data
    Given an active reservation for tail "N12345" is checked out
    And Radarcape reports tail "N12345" at bearing 278 degrees, distance 9 NM, altitude 1600 feet, and ground speed 97 knots
    When the Flight Operations screen is built
    Then the reservation line includes tail "N12345"
    And the BRG/ALT/GS field shows the home airport, bearing, distance, altitude in hundreds of feet, and ground speed
    And the line uses the scheduled flight color unless a more specific ground status applies

  Scenario: Reservations without checkout do not show ADS-B position fields
    Given an active reservation for tail "N12345" is not checked out
    And Radarcape reports tail "N12345" in flight
    When the Flight Operations screen is built
    Then the reservation remains listed
    And the ADS-B position fields for that reservation are blank

  Scenario: ADS-B aircraft without a checked-out reservation are shown as NO-CO
    Given no checked-out reservation exists for tail "N98765"
    And Radarcape reports tail "N98765" in flight
    When the Flight Operations screen is built
    Then the screen includes tail "N98765"
    And the remarks include "NO-CO"
    And the line uses the unscheduled flight color

  Scenario Outline: Position remarks summarize aircraft status
    Given an aircraft is <distance_nm> NM from the home airport
    And its altitude is <altitude_ft> feet
    And its ground speed is <ground_speed_kt> knots
    And its ground flag is <ground_flag>
    When the Flight Operations screen formats the aircraft
    Then the remarks include "<remark>"

    Examples:
      | distance_nm | altitude_ft | ground_speed_kt | ground_flag | remark |
      | 1           | 728         | 1               | true        | RAMP   |
      | 1           | 728         | 10              | true        | TAXI   |
      | 1           | 1700        | 100             | false       | PATN   |
      | 1           | 800         | 100             | false       | LOW    |
      | 3           | 3000        | 100             | false       | NEAR   |

  Scenario: Long flight lists show only displayable rows and report omitted rows
    Given the display can show 8 flight rows before the footer and METAR line
    And there are 10 formatted flight rows
    When the Flight Operations screen is built
    Then the first 8 flight rows are displayed
    And the footer says "... 2 MORE..."
    And the final line shows the short METAR

  Scenario: Missing crew and tail values are displayed without breaking the screen
    Given an active reservation has no pilot, no instructor, and no usable tail number
    When the Flight Operations screen is built
    Then the crew fields are blank
    And the aircraft field shows "------"
