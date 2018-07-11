#!/usr/bin/python

import argparse
import re
import os

parser = argparse.ArgumentParser()
parser.add_argument("--events", type=int)
parser.add_argument("--tracefile")
args = parser.parse_args()

nb_events = args.events
trace_filename = args.tracefile

trace = open(trace_filename, "r")

regex = "<event[\w\W\s]*?</event>\n"

pattern = re.compile(regex)
matches = pattern.findall(trace.read())

for i in range(0,nb_events):
	new_name = os.path.splitext(trace_filename)[0]+"_"+str(i+1)+".xml"
	new_trace = open(new_name, "w")
	line_count = 0
	for line in matches:
		if (line_count != i):		
			new_trace.write(line)
		line_count+=1
	new_trace.close()

trace.close()





