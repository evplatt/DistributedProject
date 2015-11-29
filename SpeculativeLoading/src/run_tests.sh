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

# create an array with entries for the desired number of servers
declare -a srv_ids

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

# test front-end, starts the specified number of servers;
# a test name is required...if a directory exists with that
# name and it contains a file "setup-<id>.txt" for any of
# the IDs between 0 and the maximum number of servers, then
# those commands are included in the standard input of the
# corresponding server (in this way, zero or more nodes can
# be given scripted instructions for the test)
run_test() {
  test_name=$1
  cd `dirname $0`
  # generate common setup lines with server configurations
  port=${base_port}
  server_info=
  for ((i=1; i<=${nserv}; ++i)) ; do
    server_info="${server_info}${server_host}:${port}\n"
    ((++port))
  done
  # start servers
  for ((i=0; i<${nserv}; ++i)) ; do
    # generate standard input for specified server
    stdin_data="$((i + 1))\n${nserv}\n${server_info}"
    # if any parameters are given, include them
    setup_file="${test_name}/setup-${i}.txt"
    if [ -r "$setup_file" ] ; then
      stdin_data="${stdin_data}`cat $setup_file`";
    fi
    printf "sending '${stdin_data}' to new server...\n"
    printf "${stdin_data}" | java Node &
    srv_ids[$i]=$!
  done
  sleep 30 # arbitrary (increase to length of test)
  kill_all
}

# sample test case
t_sample() {
  echo "sample ______"
  run_test ./test-sample
}




#
# tests to be run:
#
# comment-out unwanted tests (note that each test starts
# its own set of servers so they are independent)
#

t_sample

echo "all done"
