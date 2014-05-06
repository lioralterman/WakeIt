package com.wakeit;

interface NotificationServiceInterface {
  long currentAlarmId();
  int firingAlarmCount();
  float volume();
  void acknowledgeCurrentNotification(int snoozeMinutes);
}