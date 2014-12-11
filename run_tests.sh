#!/bin/sh

TESTS=`find tests -perm -u+x -type f`;
EXCLUDE="tests/timerQuality tests/tracerTest"
MATCH=0
EXIT_STATUS=0
PBS_OUT_DIR=~/buildbot_output
mkdir -p $PBS_OUT_DIR
rm -f $PBS_OUT_DIR/*
LOG=$PBS_OUT_DIR/pbs_submit_log

if [ "$TESTS" = "" ]; then
  exit 1;
fi
if [ "$MPI_COMMAND" = "" ]; then
  echo "MPI_COMMAND must be set as an environment variable";
  exit 1;
fi
echo $ECHO_SUPPRESS_NEWLINE_GNU "The following tests will be run: $ECHO_SUPPRESS_NEWLINE_AIX";
for i in $TESTS;
do
  for xtest in $EXCLUDE;
  do
    if [ "$i" = "$xtest" ]; then
      MATCH=1
      break
    fi;
  done
  if [ `basename $i` = "Makefile" ]; then
    MATCH=1
  fi;
  if [ $MATCH -ne 1 ]; then
    echo $ECHO_SUPPRESS_NEWLINE_GNU "$i $ECHO_SUPPRESS_NEWLINE_AIX"
  fi;
  MATCH=0
done
echo ""
echo $ECHO_SUPPRESS_NEWLINE_GNU "The following test will NOT be run: $ECHO_SUPPRESS_NEWLINE_AIX";
echo $EXCLUDE;
for i in $TESTS;
do
  for xtest in $EXCLUDE;
  do
    if [ "$i" = "$xtest" ]; then
      MATCH=1
      break
    fi;
  done
  if [ `basename $i` = "Makefile" ]; then
    MATCH=1
  fi;
  if [ $MATCH -ne 1 ]; then
    if [ -x $i ]; then
      exe=$i
      exe=${exe#*/}
      echo $ECHO_SUPPRESS_NEWLINE_GNU "Running $exe: $ECHO_SUPPRESS_NEWLINE_AIX";
      if [ $(hostname) = "barker" -o $(hostname) = "trebek" ]; then
        $MPI_COMMAND $i 
        if [ $? -eq 1 ]; then
          EXIT_STATUS=1
        fi
      else 
        # Create a batch script    
        cat > $exe <<-EOF
		#PBS -A HPCMO99900990
		#PBS -j oe
		#PBS -o $PBS_OUT_DIR/$exe.out
		#PBS -l walltime=00:15:00
		#PBS -l $PBS_DIRECTIVE1
		#PBS -l $PBS_DIRECTIVE2
		#PBS -q debug
		$EXTRA_BATCH_COMMAND
		$MPI_COMMAND $i
		echo "$exe executing on \$HOSTNAME" >> $LOG
	EOF
        # Put the script in the queue
        echo "submitting $exe" >> $LOG
        submsg=`qsub $exe 2>&1`
        echo "$submsg" >> $LOG
        if [ $? -ne 0 ]; then
          echo "ERROR - Job failed to queue!"
          echo "$submsg"
          EXIT_STATUS=1
          continue
        fi;
        # Wait for the process to run
        t=0
        while [ ! -f $PBS_OUT_DIR/$exe.out ]; do
          sleep 60
          t=$[$t+1]
        done
        # Process result
        if [ $? -eq 0 ]; then
          echo PASSED
        else
          echo FAILED
          EXIT_STATUS=1
        fi
        mv $exe $PBS_OUT_DIR
        echo >> $LOG
      fi;
    fi;
  fi;
  MATCH=0
done
exit $EXIT_STATUS
