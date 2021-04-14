#!/bin/bash
if [ $1 ] && [ $2 ]
then
	path_to_jemalloc=$(ldconfig -p | grep libjemalloc.so | cut -d'>' -f 2 | head -n 1)
	if [ $path_to_jemalloc ]
	then
		echo "Starting sidechain..."
		LD_PRELOAD="$path_to_jemalloc" java -cp $1/target/sidechains-sdk-simpleapp-0.2.7.jar:$1/target/lib/* com.horizen.examples.SimpleApp $2
	else
		echo "Could not find jemalloc library. Please install jemalloc to keep memory consumption in check." 
	fi
else
	echo "Usage: runsh.sh <path_to_app> <path_to_config_file>"
fi
