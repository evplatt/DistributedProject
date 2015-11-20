#!/bin/bash

# This script can be used to start a certain number of servers
# and feed them input from a client.
#
# Comment-out commands at the bottom to disable those tests.
# Every test has arbitrary standard input.
#
# EE 382N: Distributed Systems - Term Project

# set number of servers
nserv=$1
if [ "x$nserv" = "x" ] ; then
  echo "$0: specify number of servers that are running" >&2
  exit 1
fi

# set base TCP port number
base_port=$2
if [ "x$base_port" = "x" ] ; then
  echo "$0: specify base local port number for servers" >&2
  exit 1
fi

# same host for now
server_host=localhost

# create an array with entries for the desired number
# of servers (this also requires creating appropriate
# "input-srv<num>.txt" with server configurations)
#declare -a srv_ids=(0)
#declare -a srv_ids=(0 0)
declare -a srv_ids=(0 0 0)

kill_all() {
  echo "killing processes..."
  for ((i=0; i<${#srv_ids[@]}; ++i)) ; do
    kill ${srv_ids[$i]}
  done
}

cleanup_exit() {
  kill_all
  exit 1
}

trap cleanup_exit INT

# test front-end; runs the Java program
run_test() {
  cd `dirname $0`
  # start servers:
  for ((i=0; i<${#srv_ids[@]}; ++i)) ; do
    java Server < input-srv${i}.txt &
    srv_ids[$i]=$!
  done
  for data_param in "$@" ; do
    if [ "x$data_param" = "x" ] ; then
      echo "$0: improper use of run_test (expected printf-encoded string input)" >&2
      exit 1
    fi
    stdin_data="${nserv}\n"
    port=${base_port}
    for ((i=1; i<=${nserv}; ++i)) ; do
      stdin_data="${stdin_data}${server_host}:${port}\n"
      ((++port))
    done
    stdin_data="${stdin_data}${data_param}"
    sleep 1
    printf "sending '${stdin_data}' to new client...\n"
    printf "${stdin_data}" | java Client
  done
  sleep 2
  kill_all
  sleep 2
}

# send a garbage command
t_bad_command() {
  echo "bad command ______"
  run_test "sdfkjsfksj\n"
  echo "output above SHOULD include:"
  echo "ERROR: No such command"
}

# send sample command
t_sample_command() {
  echo "sample command ______"
  run_test "sample 42\n"
  echo "output above SHOULD include:"
  echo "(indications that all servers have updated their values to 42)"
}




#
# tests to be run:
#
# comment-out unwanted tests (note that each test starts
# its own set of servers so they are independent)
#

#t_bad_command
t_sample_command

echo "all done"
