# Command completion for osc
# Generated by Clikt


### Setup for osc
set -l osc_subcommands 'auth runs'

## Options for osc
complete -c osc -n "not __fish_seen_subcommand_from $osc_subcommands" -l version -s v -d 'Show the version of the CLI and the ORT Server if authenticated.'
complete -c osc -n "not __fish_seen_subcommand_from $osc_subcommands" -l json -d 'Print CLI messages as JSON.'
complete -c osc -n "not __fish_seen_subcommand_from $osc_subcommands" -l stacktrace -d 'Print the stacktrace of any error.'
complete -c osc -n "not __fish_seen_subcommand_from $osc_subcommands" -s h -l help -d 'Show this message and exit'


### Setup for auth
set -l osc_auth_subcommands 'login logout'
complete -c osc -f -n __fish_use_subcommand -a auth -d 'Commands for authentication with the ORT Server.'

## Options for auth
complete -c osc -n "__fish_seen_subcommand_from auth" -s h -l help -d 'Show this message and exit'


### Setup for login
complete -c osc -f -n "__fish_seen_subcommand_from auth; and not __fish_seen_subcommand_from $osc_auth_subcommands" -a login -d 'Login to an ORT Server instance.'

## Options for login
complete -c osc -n "__fish_seen_subcommand_from login" -l url -r -d 'The base URL of the ORT Server instance without the \'/api/v1\' path.'
complete -c osc -n "__fish_seen_subcommand_from login" -l token-url -r -d 'The URL to request a token for the ORT Server instance.'
complete -c osc -n "__fish_seen_subcommand_from login" -l client-id -r -d 'The client ID to authenticate with the ORT Server instance.'
complete -c osc -n "__fish_seen_subcommand_from login" -l username -r -d 'The username to authenticate with the ORT Server instance.'
complete -c osc -n "__fish_seen_subcommand_from login" -l password -r -d 'The password to authenticate with the ORT Server instance.'
complete -c osc -n "__fish_seen_subcommand_from login" -s h -l help -d 'Show this message and exit'


### Setup for logout
complete -c osc -f -n "__fish_seen_subcommand_from auth; and not __fish_seen_subcommand_from $osc_auth_subcommands" -a logout -d 'Logout from all ORT Server instances.'

## Options for logout
complete -c osc -n "__fish_seen_subcommand_from logout" -s h -l help -d 'Show this message and exit'


### Setup for runs
set -l osc_runs_subcommands 'download info start'
complete -c osc -f -n __fish_use_subcommand -a runs -d 'Commands to manage runs.'

## Options for runs
complete -c osc -n "__fish_seen_subcommand_from runs" -s h -l help -d 'Show this message and exit'


### Setup for download
set -l osc_runs_download_subcommands 'logs reports'
complete -c osc -f -n "__fish_seen_subcommand_from runs; and not __fish_seen_subcommand_from $osc_runs_subcommands" -a download -d 'Commands to download files for a run.'

## Options for download
complete -c osc -n "__fish_seen_subcommand_from download" -s h -l help -d 'Show this message and exit'


### Setup for logs
complete -c osc -f -n "__fish_seen_subcommand_from download; and not __fish_seen_subcommand_from $osc_runs_download_subcommands" -a logs -d 'Download a ZIP archive with logs for a run.'

## Options for logs
complete -c osc -n "__fish_seen_subcommand_from logs" -l run-id -r -d 'The ID of the ORT run.'
complete -c osc -n "__fish_seen_subcommand_from logs" -l repository-id -r -d 'The ID of the repository.'
complete -c osc -n "__fish_seen_subcommand_from logs" -l index -r -d 'The index of the ORT run.'
complete -c osc -n "__fish_seen_subcommand_from logs" -l output-dir -s o -r -d 'The directory to download the logs to.'
complete -c osc -n "__fish_seen_subcommand_from logs" -l level -r -fa "DEBUG INFO WARN ERROR" -d 'The log level of the logs to download, one of DEBUG, INFO, WARN, ERROR.'
complete -c osc -n "__fish_seen_subcommand_from logs" -l steps -r -fa "CONFIG ANALYZER ADVISOR SCANNER EVALUATOR REPORTER NOTIFIER" -d 'The run steps for which logs are to be retrieved, separated by commas.'
complete -c osc -n "__fish_seen_subcommand_from logs" -s h -l help -d 'Show this message and exit'


### Setup for reports
complete -c osc -f -n "__fish_seen_subcommand_from download; and not __fish_seen_subcommand_from $osc_runs_download_subcommands" -a reports -d 'Download reports for a run.'

## Options for reports
complete -c osc -n "__fish_seen_subcommand_from reports" -l run-id -r -d 'The ID of the ORT run, or the latest one started via osc.'
complete -c osc -n "__fish_seen_subcommand_from reports" -l repository-id -r -d 'The ID of the repository.'
complete -c osc -n "__fish_seen_subcommand_from reports" -l index -r -d 'The index of the ORT run.'
complete -c osc -n "__fish_seen_subcommand_from reports" -l file-names -l filenames -r -d 'The names of the files to download, separated by commas. If not provided, all report files will be downloaded.'
complete -c osc -n "__fish_seen_subcommand_from reports" -l output-dir -s o -r -d 'The directory to download the reports to. If not provided, the current working directory will be used.'
complete -c osc -n "__fish_seen_subcommand_from reports" -s h -l help -d 'Show this message and exit'


### Setup for info
complete -c osc -f -n "__fish_seen_subcommand_from runs; and not __fish_seen_subcommand_from $osc_runs_subcommands" -a info -d 'Print information about a run.'

## Options for info
complete -c osc -n "__fish_seen_subcommand_from info" -l run-id -r -d 'The ID of the ORT run.'
complete -c osc -n "__fish_seen_subcommand_from info" -l repository-id -r -d 'The ID of the repository.'
complete -c osc -n "__fish_seen_subcommand_from info" -l index -r -d 'The index of the ORT run.'
complete -c osc -n "__fish_seen_subcommand_from info" -s h -l help -d 'Show this message and exit'


### Setup for start
complete -c osc -f -n "__fish_seen_subcommand_from runs; and not __fish_seen_subcommand_from $osc_runs_subcommands" -a start -d 'Start a new run.'

## Options for start
complete -c osc -n "__fish_seen_subcommand_from start" -l repository-id -r -d 'The ID of the repository.'
complete -c osc -n "__fish_seen_subcommand_from start" -l wait -d 'Wait for the run to finish.'
complete -c osc -n "__fish_seen_subcommand_from start" -l parameters-file -r -F -d 'The path to a JSON file containing the run configuration (see https://eclipse-apoapsis.github.io/ort-server/api/post-ort-run).'
complete -c osc -n "__fish_seen_subcommand_from start" -l parameters -r -d 'The run configuration as a JSON string (see https://eclipse-apoapsis.github.io/ort-server/api/post-ort-run).'
complete -c osc -n "__fish_seen_subcommand_from start" -s h -l help -d 'Show this message and exit'

