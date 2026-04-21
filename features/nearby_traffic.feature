Feature: Nearby Traffic screen
  The Nearby Traffic screen must summarize regional ADS-B traffic by proximity, fleet status, and aircraft state.

  Background:
    Given the nearby traffic filter includes aircraft within 15 NM
    And the nearby traffic altitude range is 0 through 4000 feet

  Scenario: Nearby traffic is filtered by distance and altitude
    Given Radarcape reports these aircraft:
      | tail   | distance_nm | altitude_ft |
      | N111AA | 5           | 1500        |
      | N222BB | 16          | 1500        |
      | N333CC | 5           | 4500        |
      | N444DD | 5           |             |
    When SkillBoard refreshes nearby traffic
    Then only tail "N111AA" is kept for the Nearby Traffic screen

  Scenario: Traffic is sorted by distance from the home airport
    Given nearby ADS-B traffic includes:
      | tail | distance_nm |
      | N1   | 5           |
      | N2   | 1           |
      | N3   | 3           |
    When the Nearby Traffic screen is built
    Then the traffic rows are ordered "N2", "N3", "N1"

  Scenario Outline: Traffic line color reflects fleet and ground status
    Given nearby ADS-B traffic includes tail "<tail>"
    And the active fleet contains "<fleet_tail>"
    And the aircraft is <ground_state>
    When the Nearby Traffic screen is built
    Then the line for "<tail>" uses the <color> color

    Examples:
      | tail   | fleet_tail | ground_state | color         |
      | N12345 | N12345     | airborne     | in-fleet      |
      | N12345 | N99999     | airborne     | out-of-fleet  |
      | N12345 | N12345     | on ground    | on-ground     |

  Scenario: Unknown traffic tail numbers are still displayed
    Given nearby ADS-B traffic includes an aircraft with no registration
    When the Nearby Traffic screen is built
    Then the aircraft line begins with "UNKNOWN"

  Scenario: Nearby Traffic screen is padded and ends with current METAR
    Given the display has 10 text rows
    And there is 1 nearby aircraft
    When the Nearby Traffic screen is built
    Then the first row shows that aircraft
    And rows 2 through 9 are blank
    And row 10 shows the short METAR

  Scenario: Traffic rows are logged once after a poll
    Given nearby traffic logging is enabled
    And the Nearby Traffic screen contains three aircraft rows
    When the Nearby Traffic screen is built
    Then each aircraft row is written to the status log
    And nearby traffic logging is disabled

