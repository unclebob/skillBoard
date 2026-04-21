Feature: Weather screen
  The Weather screen must show forecast changes and recent observations with aviation category colors.

  Scenario: TAF text is split into displayable forecast segments
    Given Aviation Weather returns this TAF:
      """
      TAF KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 TEMPO 1212/1216 5SM -RA BR OVC015 PROB30 121600 25012KT P6SM BKN020 BECMG131200 22005KT P6SM SCT250 FM141200 22005KT P6SM SCT250
      """
    When the Weather screen is built
    Then the TAF section starts with "TAF KORD"
    And it includes separate rows for the initial forecast, "TEMPO", "PROB30", "BECMG", and "FM" segments

  Scenario Outline: TAF forecast rows are colored by flight category
    Given a TAF forecast row has visibility "<visibility>" and ceiling "<ceiling>"
    When the Weather screen colors the row
    Then the row uses the <category> color

    Examples:
      | visibility | ceiling | category |
      | 10SM       | CLR     | VFR      |
      | 5SM        | OVC015  | MVFR     |
      | 2SM        | OVC006  | IFR      |
      | 1/2SM      | OVC003  | LIFR     |

  Scenario: Amended and corrected TAF headers are preserved
    Given Aviation Weather returns a TAF beginning with "TAF AMD KORD"
    When the Weather screen is built
    Then the first TAF row is "TAF AMD KORD"
    Given Aviation Weather returns a TAF beginning with "TAF COR KORD"
    When the Weather screen is built
    Then the first TAF row is "TAF COR KORD"

  Scenario: METAR history follows the TAF section
    Given the configured TAF airport is KENW
    And the home airport is KUGN
    And Aviation Weather has recent METAR history for KUGN
    When the Weather screen is built
    Then the TAF rows are shown first
    And a blank row separates the TAF from the METAR history
    And the METAR history header says "KUGN METAR HISTORY"
    And each METAR history row omits the airport code after the METAR type

  Scenario: Missing METAR data displays NO-METAR
    Given no METAR is cached for the requested airport
    When a screen requests the short METAR
    Then it receives a line saying "NO-METAR"
    And the line uses the informational color

  Scenario: Long METAR text is truncated to the display width
    Given a METAR line is longer than 64 characters before remarks are removed
    When the short METAR is built
    Then the displayed METAR is no more than 64 characters
    And remarks after "RMK" are not displayed

