Feature: Screen cycle and keyboard controls
  SkillBoard must cycle through operational screens without operator action while still allowing manual advancement.

  Background:
    Given the configured screen sequence is:
      | screen            | duration_seconds |
      | Flight Operations | 40               |
      | Weather           | 20               |
      | Flight Category   | 20               |
      | Nearby Traffic    | 20               |

  Scenario: The display starts with the first configured screen
    When SkillBoard prepares the display
    Then the current screen is "Flight Operations"
    And the screen duration is 40 seconds

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
    Then the current screen becomes "Flight Category"
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

