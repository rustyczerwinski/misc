# jira-cli

## What is this?

This is a command line interface (CLI) for easing actions in Jira.  
This is based on the open-source tool https://github.com/go-jira

## Pre-requisites

An endpoint/client in SecLab/BigTop with the cert NetApp Corp Issuing CA 1. (see below login step for verification/action)

## User Quickstart

The following will walk you through installing 

### Step 1 - install in the path at or above the directory on which you will work

create the directory and config file as stored in this repo.
In the config file, change the user to be yourself (SSO, without the @....)
The util is configured to run against jira.eng.  If you are using a different Jira instance, such as NGAGE, update in the config file the Jira URL.

### Step 2 - Ensure connectivity to Jira

Attempt connectivity to Jira.

```bash
jira login -k
```

If get a directory error, try again as sudo

#### If get a cert error install the needed cert

1. Find the cert NetApp Corp Issuing CA 1 (Issuing CA - IT Owned).  May be available at https://confluence.ngage.netapp.com/pages/viewpage.action?pageId=168597738 .  Might also be able to get from your browser.
2. Install on your Linux client with copy/enable/extract
3. Copy the above cert to dest: /etc/pki/ca-trust/source/anchors
4. Run /usr/bin/update-ca-trust enable
5. Run /usr/bin/update-ca-trust extract     

## Commands Overview

The base tool supports many command for routine Jira operation.  The installed config can add custom commands.  The "jira" command will list first the base commands, then the custom commands.

```bash
jira
```

### Common base commands:
- create (create an issue)
- in-progress, done (transition issues)

### Currently, custom commands include:
- add-epic-story
- add-epic-bug
- my-backlog

## License

```
Copyright (c) 2020 NetApp, Inc., All Rights Reserved
Any use, modification, or distribution is prohibited
without prior written consent from NetApp, Inc.
```

