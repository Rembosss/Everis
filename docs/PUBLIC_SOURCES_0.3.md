# Public timetable sources in Everis 0.3-beta

All adapters use anonymous HTTPS requests to university services. No Everis server, university credentials or VPN bypass is used. A source outage preserves the last saved timetable.

| Source | Student timetable | Teacher timetable | Coverage |
| --- | --- | --- | --- |
| RSUE / RINH | Yes | Yes | Dated lessons published by rasp-api.rsue.ru |
| DSTU | Yes | Yes | Dated lessons published by edu.donstu.ru |
| SFedU ICTIS | Yes | Yes | ICTIS Modeus only; not the whole university |
| RGUPS | Yes | No | Full-time groups; current and next week projected from the public alternating-week cycle |
| Southern University / IMBL | Yes | No | All dated weeks returned for the selected group |

## RGUPS

Official entry: https://www.rgups.ru/services/time/

The website’s own schedule.js makes read-only form POST requests to this page. The hierarchy is faculty → course → group. Everis handles HTML rowspan, empty rows, both-week classes, subgroup labels and the active/inactive parity. The public page does not publish semester dates. The two-week date projection is disclosed during selection and on the home screen; it must not be described as a dated semester or an authoritative replacement/session feed. Instructor names remain exactly as published; initials are not expanded by guessing. Distance-study documents are not supported.

## IMBL / IUBIP

Official entry: https://iubip.ru/schedule/

The website’s public read-file-groups.php form endpoint supplies the catalog and dated weeks. Bell times are read from the official schedule JS rather than hardcoded. Combined group names are preserved. Numeric subgroups, cancellations and lesson types are retained. No teacher-search endpoint or building addresses have been confirmed; unavailable details are not invented.
