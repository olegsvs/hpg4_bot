#!/usr/bin/sh
nohup python3.10 map.py > logs/nohup_map.log &
sleep 20
cp -rf /home/bot/hpg4_bot/hpg4_map.png /home/www/static/hpg4_map.png