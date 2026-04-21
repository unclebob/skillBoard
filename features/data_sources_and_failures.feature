Feature: Polling data sources and indicating failures
  SkillBoard must keep the wall display useful when external data sources fail.

  Background:
    Given the configured poll interval is 60 seconds
    And the display uses three status lights ordered FlightSchedulePro, Radarcape, Aviation Weather

  Scenario: Periodic polling refreshes all display data sources
    When a scheduled poll occurs
    Then SkillBoard requests active aircraft, flights, and reservations from FlightSchedulePro
    And SkillBoard requests ADS-B data for active fleet tail numbers from Radarcape
    And SkillBoard requests nearby ADS-B traffic from Radarcape
    And SkillBoard requests METAR, METAR history, and TAF weather data from Aviation Weather
    And the nearby traffic log is enabled for the next traffic screen refresh

  Scenario: Manual polling can be requested from the keyboard
    Given no scheduled poll is due
    When the operator releases the "p" key
    Then SkillBoard performs a poll at the next polling check
    And the manual poll request is cleared

  Scenario: Successful source response replaces cached data and resets its failure count
    Given SkillBoard has cached data for a source
    And that source has previous communication failures
    When the source returns a successful JSON response
    Then the cached data for that source is replaced with the new response
    And the failure count for that source is reset to zero

  Scenario: Failed source response preserves the last successful data
    Given SkillBoard has cached data for a source
    When that source returns an error, invalid response, or no response body
    Then SkillBoard logs an error for that source
    And the failure count for that source increases by one
    And the display continues using the last cached data for that source

  Scenario Outline: Status light color reflects consecutive failures
    Given a source has <failures> consecutive failed poll attempts
    When the clock and status area is drawn
    Then that source's status light is <color>

    Examples:
      | failures | color  |
      | 0        | green  |
      | 1        | green  |
      | 2        | orange |
      | 3        | orange |
      | 4        | red    |

