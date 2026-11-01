#!/system/bin/sh
# Re-asserts the performance governor every 5 s: a vendor daemon resets it to ondemand within about 20 s.
echo $$ > /data/local/tmp/cpu-hold.pid
while true; do
  for c in 0 1 2 3; do echo performance > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor; done
  sleep 5
done
