Feature: Flight Category screen
  The Flight Category screen must help operators compare nearby airport weather quickly.

  Background:
    Given the configured flight category airports are polled for METAR data
    And the home airport is KUGN

  Scenario: Airports are sorted by distance from the home airport
    Given cached METAR data exists for these airports:
      | airport | distance_nm |
      | KAAA    | 30          |
      | KBBB    | 10          |
      | KCCC    | 20          |
    When the Flight Category screen is built
    Then the airport rows are ordered "KBBB", "KCCC", "KAAA"

  Scenario Outline: Airport category color follows METAR flight category
    Given an airport METAR has flight category "<category>"
    When the Flight Category screen displays that airport
    Then the row uses the <color> color

    Examples:
      | category | color   |
      | VFR      | green   |
      | MVFR     | blue    |
      | IFR      | red     |
      | LIFR     | magenta |
      |          | white   |

  Scenario: Complete METAR fields are displayed in airport category columns
    Given an airport METAR for KORD reports VFR, clear sky, 10 SM visibility, wind 15 knots, and distance 10 NM
    When the Flight Category screen displays KORD
    Then the row includes airport "KORD"
    And the category column says "VFR"
    And the sky column says "CLR"
    And the base column is blank
    And the visibility column says "10"
    And the wind column says "15"
    And the distance column says "010"

  Scenario: Gusts and cloud bases are displayed when present
    Given an airport METAR for KORD reports IFR, broken clouds at 030, 5 SM visibility, wind 10 knots gusting 15, and distance 10 NM
    When the Flight Category screen displays KORD
    Then the row includes category "IFR"
    And the sky column says "BKN"
    And the base column says "030"
    And the wind column says "10G15"

  Scenario: Missing optional airport weather fields leave blanks instead of failing
    Given an airport METAR is missing flight category, cloud cover, cloud base, and gusts
    When the Flight Category screen displays that airport
    Then the row is still displayed
    And the missing fields are blank
    And the row uses the informational color

