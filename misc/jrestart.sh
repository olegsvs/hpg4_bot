#!/usr/bin/sh
pkill -f hpg4_bot.jar
sleep 3
nohup python3.10 refresh.py > logs/nohup_refresh.log &
sleep 5
nohup java -jar hpg4_bot.jar > logs/nohup_java_log.log &