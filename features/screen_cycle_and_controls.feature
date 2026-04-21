Feature: Screen cycle and keyboard controls
  SkillBoard must cycle through operational screens without operator action while still allowing manual advancement.

  Background:
    Given the configured screen sequence is:
      | screen            | duration_seconds |
      | Flight Operations | 40               |
      | Weather           | 20               |
      | Wind Map          | 30               |
      | Nearby Traffic    | 20               |
      | Flight Operations | 40               |
      | Weather           | 20               |
      | Flight Category   | 20               |
      | Nearby Traffic    | 20               |

  Scenario: The display starts with the first configured screen
    When SkillBoard prepares the display
    Then the current screen is "Flight Operations"
    And the screen duration is 40 seconds

  Scenario: The Wind Map screen animates a local wind field
    Given Open-Meteo wind grid data is available for a 200 NM radius around KUGN
    And AviationWeather METAR cache data is available for airports within 200 NM of KUGN
    When the Wind Map screen is displayed
    Then the display shows KUGN at the center of the map
    And the display animates wind particles using the local wind vectors
    And the display plots all nearby METAR-reporting airports in flight category colors
    And the display labels only Class B, Class C, and Class D airports
    And the display labels the wind data source and radius

  Scenario: A screen advances after its configured duration
    Given the current screen is "Flight Operations"
    And the screen started at 09:00:00
    When the display refreshes at 09:00:41
    Then the current screen becomes "Weather"
    And the screen duration is 20 seconds
    And the screen change is animated from a blank split-flap board

  Scenario: A screen does not advance before its configured duration
    Given the current screen is "Weather"
    And the screen started at 09:00:00
    When the display refreshes at 09:00:19
    Then the current screen remains "Weather"

  Scenario: The space bar advances to the next configured screen
    Given the current screen is "Weather"
    When the operator releases the space bar
    And the display refreshes
    Then the current screen becomes "Wind Map"
    And the manual screen change request is cleared

  Scenario: The displayed clock blinks once per second
    Given the local time converted to UTC is 14:55
    When the clock pulse is on
    Then the clock displays "14:55"
    When the clock pulse is off
    Then the clock displays "14 55"

  Scenario: Closing the display exits the application
    When the operator closes the SkillBoard window
    Then SkillBoard logs that it closed
    And the display loop stops
    And the application exits
