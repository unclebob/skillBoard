Feature: Report SkillBoard health across private networks
  SkillBoard reports its health using outbound HTTPS so an operator can check it remotely without connecting to the flight school's network.

  Scenario: An hourly heartbeat reports current operational totals
    Given remote heartbeat reporting is configured
    And today's logs contain aircraft reports, communication failures, and application starts
    When an hourly heartbeat is due
    Then SkillBoard posts its version, timestamp, and usable disk space
    And it posts today's aircraft report, communication issue, and application start counts

  Scenario: Daily totals survive application restarts
    Given today's logs contain operational entries written before the current process started
    When SkillBoard creates a heartbeat snapshot
    Then the heartbeat includes all matching entries from today's logs

  Scenario: Heartbeat delivery is retried without changing the hourly schedule
    Given a scheduled heartbeat post fails
    When its bounded retries become due
    Then SkillBoard retries after one minute and five minutes
    And the next regular heartbeat remains on its original hourly schedule

  Scenario: Heartbeat failures do not stop the display
    Given heartbeat collection or delivery fails
    When SkillBoard continues its polling coordinator
    Then the failure is logged without disclosing the heartbeat URL
    And normal display and data polling continue

  Scenario: Heartbeat reporting is optional
    Given no heartbeat URL is configured
    When SkillBoard starts its polling coordinator
    Then remote heartbeat reporting is disabled
    And normal display and data polling continue
