#!/bin/bash 
javac LoadTest.java 
echo $SECONDS 
java LoadTest 127.0.0.1 45601 11 GET 1000 & 
java LoadTest 127.0.0.1 45601 12 GET 1000 & 
java LoadTest 127.0.0.1 45601 100 GET 1000 & 
java LoadTest 127.0.0.1 45601 101 GET 1000 & 
wait $(jobs -p) 
echo $SECONDS
