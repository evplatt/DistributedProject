test_output=$1
grep -o 'completed' $test_output | wc -l
