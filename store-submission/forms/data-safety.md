# Data safety (Play Console › Policy › App content › Data safety)

Google's definition: data is "collected" when it leaves the device. Data handled only on the device isn't collected.

1. **Does your app collect or share any of the required user data types?** No.
   - Network: none (no INTERNET permission; WorkManager's network-state permission only reads connectivity). Nothing can be sent anywhere.
   - No analytics, crash reporting, ads or other SDKs that send data.
   - On the device only: the AirPods' state and settings, two Bluetooth keys the AirPods hand out (left out of backups),
     head-gesture calibration, and heart rate (AirPods Pro 3, shown while its page is open, not stored).
2. The security questions (encryption in transit, deletion requests) don't apply when nothing is collected.

Resulting label: **No data collected. No data shared with third parties.**
