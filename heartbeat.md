# SkillBoard Remote Heartbeat

Type: component

## Objective

Allow an operator on a different private network to determine whether the
SkillBoard application is running and to inspect a small operational summary.
The Raspberry Pi must initiate all communication using ordinary outbound
HTTPS. The feature must not require a VPN, an inbound firewall rule, port
forwarding, or direct access to the Raspberry Pi.

SkillBoard shall post a JSON heartbeat to a configured Healthchecks-compatible
URL once per hour. Healthchecks.io is the initial service. The posted body shall
contain free disk space and cumulative counts for the current local calendar
day.

## Heartbeat Payload

Each heartbeat shall contain the following information:

```json
{
  "application": "skillBoard",
  "version": "20260907",
  "test": true,
  "reported_at": "2026-09-07T14:00:00-05:00",
  "disk": {
    "usable_bytes": 48255102976,
    "total_bytes": 68719476736,
    "usable_percent": 70.2
  },
  "today": {
    "aircraft_reports": 187,
    "unique_tail_numbers": 24,
    "reported_tails": {
      "N12345": 12
    },
    "communication_issues": 3,
    "application_starts": 2
  }
}
```

Field names and meanings are part of the external heartbeat contract.

- `application` is always `skillBoard`.
- `version` is the running value of `skillBoard.foundation.config/version`.
- `test` is present and `true` only when SkillBoard was launched with `-t`.
  The field is omitted in normal operation.
- `reported_at` is the time at which the snapshot was made, formatted as an
  ISO-8601 timestamp with a UTC offset.
- `disk.usable_bytes` is the space available to the account running
  SkillBoard on the filesystem containing the log directory.
- `disk.total_bytes` is the total size of that filesystem.
- `disk.usable_percent` is `usable_bytes / total_bytes * 100`, rounded to one
  decimal place. If the total size is unavailable or zero, this value is
  `null`.
- `today.aircraft_reports` is the number of traffic entries written to the
  current day's status log. Each line whose message begins `Traffic:` is one
  report. Repeated reports of the same aircraft are counted separately.
- `today.unique_tail_numbers` is the number of distinct tail numbers parsed
  from those `Traffic:` aircraft-report lines. A tail is the first token after
  `Traffic:`. Lines that carry the aircraft-report event but are not
  `Traffic:` lines do not contribute a tail.
- `today.reported_tails` is a map of each tail listed in
  `private/reported-tails` to the number of today's `Traffic:` aircraft-report
  lines for that tail. Matching is exact after trimming. A listed tail with a
  count of zero is omitted. Duplicate lines in the file are counted once. If
  the file is missing, or no listed tail appeared today, this value is `{}`.
- `today.communication_issues` is the number of communication-failure entries
  written to the current day's error log. A communication-failure entry is a
  log message whose message begins `Error fetching `. Other application,
  rendering, parsing, and heartbeat-posting errors are not included.
- `today.application_starts` is the number of entries in the current day's
  status log whose message reports `skillBoard v... has begun.`. This is the
  observable restart measure. It deliberately reports process starts rather
  than attempting to distinguish a normal launch, a reboot, and recovery from
  a crash.

The daily counts are cumulative. They naturally return to zero when the local
date changes and the new day's log files do not yet contain matching entries.
If SkillBoard has been running since a previous day,
`today.application_starts` is zero until it starts again.

The log date and log paths used by the heartbeat must come from the same logic
used by the logger. The heartbeat must not independently duplicate filename or
date-boundary rules. The deployed system's local time zone is
`America/Chicago`.

## Scheduling

- Start the heartbeat scheduler only after secure configuration has been
  loaded.
- Make the first heartbeat immediately after the scheduler starts.
- Schedule subsequent heartbeats at 60-minute intervals measured from the
  original schedule, rather than 60 minutes after a request finishes.
- Claim a scheduled time before starting network IO so that the application's
  fast polling loop cannot launch duplicate posts.
- Never allow two heartbeat posts to overlap.
- Heartbeat collection and network IO must run outside the Quil draw/update
  thread and must not delay the existing one-minute data poll.
- Stopping or failing the application's polling coordinator must also stop
  new heartbeats. A coordinator failure must therefore become observable as a
  missing heartbeat.

On a failed request, retry after one minute and again after five minutes. A
successful retry completes that scheduled heartbeat. Retries do not shift the
next regular hourly time. There shall be no further retry for that hour, and a
retry still in progress shall not overlap the next regular post.

## Posting Behavior

- Read the destination from `:heartbeat-url` in `private/config`.
- Read selected tail numbers from `private/reported-tails`. The file is plain
  text, one tail per line. Blank lines and whole-line `#` comments are
  ignored. A missing file does not fail the heartbeat; `reported_tails` is
  `{}`.
- Send an HTTPS `POST` with a UTF-8 JSON body and an
  `application/json` content type.
- Treat an HTTP 2xx response as success.
- Use finite connection and socket timeouts; neither may exceed ten seconds.
- Catch collection, serialization, and transport failures at the heartbeat
  boundary. Log the failure locally without terminating SkillBoard or its
  normal data polling.
- Do not include the secret destination URL in log messages or exceptions
  written to disk.
- If `:heartbeat-url` is absent or blank, do not start the scheduler. Log one
  status message explaining that remote heartbeat reporting is disabled.
- A failure to read one daily log because it does not exist means a count of
  zero. Other filesystem failures are heartbeat failures and must not be
  silently converted to zero.
- A heartbeat post reports that SkillBoard is alive even when one or more
  upstream data sources have had communication issues. Those issues are
  represented by the payload count; they do not change the heartbeat request
  to an explicit Healthchecks failure signal.

## Privacy and Security

- The heartbeat URL is a secret bearer credential. It belongs only in
  `private/config`, which is excluded from source control.
- The URL must never be committed, displayed on the SkillBoard screen, or
  written to a log.
- Do not send pilot or instructor names, reservations, METAR contents, stack
  traces, IP addresses, or other operational details.
- The only aircraft identifiers in the payload are the operator-chosen tail
  numbers listed in `private/reported-tails`, each paired with a count.
  `unique_tail_numbers` is an aggregate and does not include the
  registrations themselves. Do not send the rest of the day's tail list.
- The Raspberry Pi does not listen for remote heartbeat connections.
- A hard-to-guess badge URL may be kept separately on the operator's laptop for
  status-only access. Retrieving ping bodies through the Management API
  requires a read-write project API key. That key must remain on the laptop and
  is not part of the SkillBoard configuration.

## Healthchecks Operation

The Healthchecks check should expect a heartbeat every 60 minutes and use a
15-minute grace period. In normal operation the remote status is up. When a
heartbeat is overdue it becomes late, and after the grace period it becomes
down. This produces a worst-case failure-detection delay of approximately 75
minutes.

The operator may use the Healthchecks dashboard or badge JSON for the
up/late/down indicator. The dashboard displays stored ping bodies. An
authenticated Management API client may retrieve the most recent ping body
when metric values are needed, but Healthchecks requires a read-write project
API key for the ping-history and ping-body endpoints.

Creating the Healthchecks account, configuring notifications, and implementing
a laptop dashboard are outside this component. Document the manual service
configuration and how to verify one received heartbeat.

## Acceptance Behaviors

The executable specification should demonstrate at least the following
behavior without contacting a live internet service:

1. Starting with a configured URL posts an immediate correctly structured
   snapshot and schedules hourly snapshots.
2. Repeated scheduler checks cannot produce duplicate or overlapping posts.
3. The next regular post remains on its original schedule after a retry.
4. Traffic lines, communication-failure lines, and application-start lines are
   counted according to the definitions above.
5. Repeated entries for one aircraft count as repeated aircraft reports.
6. Unique tail numbers count distinct registrations in today's `Traffic:`
   lines. `reported_tails` counts instances of each tail listed in
   `private/reported-tails`. Listed tails with a count of zero are omitted.
   A missing `private/reported-tails` file yields an empty map.
7. Unrelated status and error messages do not affect the daily counts.
8. Missing current-day log files produce zero daily counts and an empty
   `reported_tails` map.
9. Counts reset when the local date changes, including while the application
   remains running.
10. A process restart reconstructs the daily counts from the logs rather than
    losing prior values.
11. Disk measurements describe the filesystem containing the log directory and
    the percentage calculation handles a zero total size.
12. A transient posting failure follows the bounded retry schedule.
13. Collection or posting failure does not stop display updates or normal data
    polling and does not disclose the heartbeat URL.
14. Missing heartbeat configuration disables reporting without affecting the
    rest of SkillBoard.
15. The JSON contains no personal or reservation data. The only aircraft
    identifiers are the operator-selected tails in `reported_tails`.

Clock, log access, disk measurement, and HTTP posting must be replaceable at
the test boundary. Acceptance and unit tests must use controlled substitutes;
they must not depend upon the machine's actual disk, wall clock, existing log
files, or Healthchecks.io.

## Documentation

Update the README configuration example to show the optional
`:heartbeat-url`. Explain that the URL is secret, that an absent URL disables
the feature, what each reported metric means, and that hourly reporting can
take approximately 75 minutes to indicate an outage.

## Out of Scope

- VPNs, tunnels, inbound network access, port forwarding, or dynamic DNS.
- Remote control or restart of the Raspberry Pi.
- A new SkillBoard screen or other visible UI change.
- Uploading log contents or aircraft information beyond the operator-selected
  `reported_tails` counts.
- Long-term metric storage inside SkillBoard.
- Distinguishing intentional launches from crash recovery or machine reboots.
- Monitoring whether the display is physically visible or whether the monitor
  is powered on.
