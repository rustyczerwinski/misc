#!/bin/bash
DEBUG=false

# capture input variables###############################
epicVar=$1
summaryVar="$2"
# typeVar optional defaults to Story (Jira needs proper case)
typeVar=$3
if [ -z $typeVar ]
then
	typeVar="Story"
fi
returnVal=0

echo "add_epic_issue epic $epicVar type $typeVar summary '$summaryVar'" > test.log
$DEBUG && echo $@ $?

# validate input vars
if [ -z "$epicVar" ] || [ -z "$summaryVar" ] 
then
	echo "missing required parameter (epic='epicVar' summary='$summaryVar')"
	exit 1
fi
#########################################

# create the new ticket (output to file to preserve/parse)
jira create --issuetype=$typeVar -o summary="$summaryVar" -o reporter=" " --noedit > _new_ticket.log

# if command not successful, or if output does not give the new ticket id as expected, exit with failure
if [ "0" != "$?" ] 
then 
	echo "error creating new ticket.  Will not update Epic" ; cat _new_ticket.log
	exit 1
fi
while read OKVar ticketVar URLVar 
do
	if [ -z "$OKVar" ]
	then
		echo "error creating new ticket" ; cat _new_ticket.log
 		exit 1
	fi 

        
	# created new ticket, now add to Epic
	$DEBUG && echo create new ticket succeeeded, OKVar $OKVar ticketVar $ticketVar URLVar $URLVar >> test.log
        jira epic add $epicVar $ticketVar >> test.log
        
	# if command fails, exit with failure
	if [ "0" != "$?" ] 
        then 
		echo "add to Epic failed"; cat test.log
		exit 1
	else
		# success, show user
		echo "$ticketVar added to Epic $epicVar"
	fi 
done < _new_ticket.log

$DEBUG && cat test.log
exit 0
