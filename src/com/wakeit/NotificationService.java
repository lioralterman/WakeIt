/****************************************************************************
 * Copyright 2010 kraigs.android@gmail.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 ****************************************************************************/

package com.wakeit;

import java.util.LinkedList;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;

import com.wakeit.R;
import com.wakeit.WakeLock.WakeLockException;

/**
 * This service is responsible for notifying the user when an alarm is
 * triggered.  The pending intent delivered by the alarm manager service
 * will trigger the alarm receiver.  This receiver will in turn start
 * this service, passing the appropriate alarm url as data in the intent.
 * This service is capable of receiving multiple alarm notifications
 * without acknowledgments and will queue them until they are sequentially
 * acknowledged.  The service is capable of playing a sound, triggering
 * the vibrator and displaying the notification activity (used to acknowledge
 * alarms).
 */
public class NotificationService extends Service implements SensorEventListener{
  public class NoAlarmsException extends Exception{
    private static final long serialVersionUID = 1L;
  }

  private SensorManager senSensorManager;
  private Sensor senAccelerometer;
  private long lastUpdate, counterUpdate = System.currentTimeMillis();;
  private float last_x, last_y, last_z, speed;
  private static final int SHAKE_THRESHOLD = 30;
  int counter=0;
  boolean sleepFlag = true;
  boolean toWork=false;
  public static int counterTotal=0;
  public static String clock_text_g="Wake Up";
  private float start;
  private int idx=0;
  private int jdx=0;
  
  
  final public static int timeToShake = 60;// how much time until the subject is fully awake. in sex.
  final public static int checkInterval = 10;// how much time of not-moving means being asleep. in sex.
  

  // Since the media player objects are expensive to create and destroy,
  // share them across invocations of this service (there should never be
  // more than one instance of this class in a given application).
  private enum MediaSingleton {
    INSTANCE;

    private MediaPlayer mediaPlayer = null;
    private Ringtone fallbackSound = null;
    private Vibrator vibrator = null;
    private int systemNotificationVolume = 0;

    MediaSingleton() {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
    }

    // Force the alarm stream to be maximum volume.  This will allow the user
    // to select a volume between 0 and 100 percent via the settings activity.
    private void normalizeVolume(Context c, float startVolume) {
      final AudioManager audio =
        (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      systemNotificationVolume =
          audio.getStreamVolume(AudioManager.STREAM_ALARM);
      audio.setStreamVolume(AudioManager.STREAM_ALARM,
          audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
      setVolume(startVolume);
    }

    private void setVolume(float volume) {
      mediaPlayer.setVolume(volume, volume);
    }

    private void resetVolume(Context c) {
      final AudioManager audio =
        (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      audio.setStreamVolume(
          AudioManager.STREAM_ALARM, systemNotificationVolume, 0);
    }

    private void useContext(Context c) {
      // The media player can fail for lots of reasons.  Try to setup a backup
      // sound for use when the media player fails.
      fallbackSound = RingtoneManager.getRingtone(c, AlarmUtil.getDefaultAlarmUri());
      if (fallbackSound == null) {
        Uri superFallback = RingtoneManager.getValidRingtoneUri(c);
        fallbackSound = RingtoneManager.getRingtone(c, superFallback);
      }
      // Make the fallback sound use the alarm stream as well.
      if (fallbackSound != null) {
        fallbackSound.setStreamType(AudioManager.STREAM_ALARM);
      }

      // Instantiate a vibrator.  That's fun to say.
      vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void ensureSound() {
      if (!mediaPlayer.isPlaying() &&
          fallbackSound != null && !fallbackSound.isPlaying()) {
        fallbackSound.play();
      }
    }

    private void vibrate() {
      if (vibrator != null) {
        vibrator.vibrate(new long[] {500, 500}, 0);
      }
    }

    public void play(Context c, Uri tone) {
      mediaPlayer.reset();
      mediaPlayer.setLooping(true);
      try {
        mediaPlayer.setDataSource(c, tone);
        mediaPlayer.prepare();
        mediaPlayer.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void stop() {
      mediaPlayer.stop();
      if (vibrator != null) {
        vibrator.cancel();
      }
      if (fallbackSound != null) {
        fallbackSound.stop();
      }
    }
  }

  // Data
  private LinkedList<Long> firingAlarms;
  private AlarmClockServiceBinder service;
  private DbAccessor db;
  // Notification tools
  private NotificationManager manager;
  private Notification notification;
  private PendingIntent notificationActivity;
  private Handler handler;
  private VolumeIncreaser volumeIncreaseCallback; 
  private Runnable soundCheck;
  private Runnable notificationBlinker;
  private Runnable autoCancel;

  @Override
  public IBinder onBind(Intent intent) {
    return new NotificationServiceInterfaceStub(this);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    
    senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    senSensorManager.registerListener(this, senAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
    
    firingAlarms = new LinkedList<Long>();
    // Access to in-memory and persistent data structures.
    service = new AlarmClockServiceBinder(getApplicationContext());
    service.bind();
    db = new DbAccessor(getApplicationContext());

    // Setup audio.
    MediaSingleton.INSTANCE.useContext(getApplicationContext());

    // Setup notification bar.
    manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    // Use the notification activity explicitly in this intent just in case the
    // activity can't be viewed via the root activity.
    Intent intent = new Intent(getApplicationContext(), ActivityAlarmNotification.class);
    notificationActivity = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    notification = new Notification(R.drawable.alarmclock, null, 0);
    notification.flags |= Notification.FLAG_ONGOING_EVENT;

    // Setup a self-scheduling event loops.
    handler = new Handler();
    volumeIncreaseCallback = new VolumeIncreaser();
    soundCheck = new Runnable() {
      @Override
      public void run() {
        // Some sound should always be playing.
        MediaSingleton.INSTANCE.ensureSound();

        long next = AlarmUtil.millisTillNextInterval(AlarmUtil.Interval.SECOND);
        handler.postDelayed(soundCheck, next);
      }
    };
    notificationBlinker = new Runnable() {
      @Override
      public void run() {
        String notifyText;
        try {
          AlarmInfo info = db.readAlarmInfo(currentAlarmId());
          notifyText = info.getName();
          if (notifyText.equals("")) {
            notifyText = info.getTime().localizedString(getApplicationContext());
          }
        } catch (NoAlarmsException e) {
          return;
        }
        notification.setLatestEventInfo(getApplicationContext(), notifyText, "", notificationActivity);
        if (notification.icon == R.drawable.alarmclock) {
          notification.icon = R.drawable.alarmclock_notification2;
        } else {
          notification.icon = R.drawable.alarmclock;
        }
        manager.notify(AlarmClockService.NOTIFICATION_BAR_ID, notification);

        long next = AlarmUtil.millisTillNextInterval(AlarmUtil.Interval.SECOND);
        handler.postDelayed(notificationBlinker, next);
      }
    };
    autoCancel = new Runnable() {
      @Override
      public void run() {
        try {
          acknowledgeCurrentNotification(0);
        } catch (NoAlarmsException e) {
          return;
        }
        Intent notifyActivity = new Intent(getApplicationContext(), ActivityAlarmNotification.class);
        notifyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifyActivity.putExtra(ActivityAlarmNotification.TIMEOUT_COMMAND, true);
        startActivity(notifyActivity);
      }
    };
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    db.closeConnections();
    service.unbind();

    boolean debug = AppSettings.isDebugMode(getApplicationContext());
    if (debug && firingAlarms.size() != 0) {
      throw new IllegalStateException("Notification service terminated with pending notifications.");
    }
    try {
      WakeLock.assertNoneHeld();
    } catch (WakeLockException e) {
      if (debug) { throw new IllegalStateException(e.getMessage()); }
    }
  }

  // OnStart was depreciated in SDK 5.  It is here for backwards compatibility.
  // http://android-developers.blogspot.com/2010/02/service-api-changes-starting-with.html
  @Override
  public void onStart(Intent intent, int startId) {
    handleStart(intent, startId);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    handleStart(intent, startId);
    
    return START_NOT_STICKY;
    
    
  }

  private void handleStart(Intent intent, int startId) {
    // startService called from alarm receiver with an alarm id url.
	  toWork=true;
    if (intent != null && intent.getData() != null) {
      long alarmId = AlarmUtil.alarmUriToId(intent.getData());
      try {
        WakeLock.assertHeld(alarmId);
      } catch (WakeLockException e) {
        if (AppSettings.isDebugMode(getApplicationContext())) {
          throw new IllegalStateException(e.getMessage());
        }
      }
      Intent notifyActivity = new Intent(getApplicationContext(), ActivityAlarmNotification.class);
      notifyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(notifyActivity);

      boolean firstAlarm = firingAlarms.size() == 0;
      if (!firingAlarms.contains(alarmId)) {
        firingAlarms.add(alarmId);
      }

      if (firstAlarm) {
        soundAlarm(alarmId);
      }
    }
  }

  public long currentAlarmId() throws NoAlarmsException {
    if (firingAlarms.size() == 0) {
      throw new NoAlarmsException();
    }
    return firingAlarms.getFirst();
  }

  public int firingAlarmCount() {
    return firingAlarms.size();
  }

  public float volume() {
    return volumeIncreaseCallback.volume();
  }

  public void acknowledgeCurrentNotification(int snoozeMinutes) throws NoAlarmsException {
    long alarmId = currentAlarmId();
    if (firingAlarms.contains(alarmId)) {
      firingAlarms.remove(alarmId);
      if (snoozeMinutes <= 0) {
        service.acknowledgeAlarm(alarmId);
      } else {
        service.snoozeAlarmFor(alarmId, snoozeMinutes);
      }
    }
    stopNotifying();

    // If this was the only alarm firing, stop the service.  Otherwise,
    // start the next alarm in the stack.
    if (firingAlarms.size() == 0) {
      stopSelf();
    } else {
      soundAlarm(alarmId);
    }
    try {
      WakeLock.release(alarmId);
    } catch (WakeLockException e) {
      if (AppSettings.isDebugMode(getApplicationContext())) {
        throw new IllegalStateException(e.getMessage());
      }
    }
  }

  private void soundAlarm(long alarmId) {
    // Begin notifying based on settings for this alaram.
    AlarmSettings settings = db.readAlarmSettings(alarmId);
    if (settings.getVibrate()) {
      MediaSingleton.INSTANCE.vibrate();
    }

    volumeIncreaseCallback.reset(settings);
    MediaSingleton.INSTANCE.normalizeVolume(
        getApplicationContext(), volumeIncreaseCallback.volume());
    MediaSingleton.INSTANCE.play(getApplicationContext(), settings.getTone());

    // Start periodic events for handling this notification.
    handler.post(volumeIncreaseCallback);
    handler.post(soundCheck);
    handler.post(notificationBlinker);
    // Set up a canceler if this notification isn't acknowledged by the timeout.
    int timeoutMillis = 60 * 1000 * AppSettings.alarmTimeOutMins(getApplicationContext());
    handler.postDelayed(autoCancel, timeoutMillis);
  }

  private void stopNotifying() {
    // Stop periodic events.
    handler.removeCallbacks(volumeIncreaseCallback);
    handler.removeCallbacks(soundCheck);
    handler.removeCallbacks(notificationBlinker);
    handler.removeCallbacks(autoCancel);

    // Stop notifying.
    MediaSingleton.INSTANCE.stop();
    MediaSingleton.INSTANCE.resetVolume(getApplicationContext());
  }
  
  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
      //To change body of implemented methods use File | Settings | File Templates.
      Sensor mySensor = sensorEvent.sensor;

      if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
          float x = sensorEvent.values[0];
          float y = sensorEvent.values[1];
          float z = sensorEvent.values[2];

          long curTime = System.currentTimeMillis();
          
          // only allow one update every 100ms.
          long diffTime = (curTime - lastUpdate);
          lastUpdate = curTime;

          speed = Math.abs(x+y+z - last_x - last_y - last_z)/ diffTime * 10000;

          if (speed > SHAKE_THRESHOLD) {
          	counterUpdate = curTime;
                 // Toast.makeText(this, "shake detected w/ speed: " + speed, Toast.LENGTH_SHORT).show();
                  //getRandomNumber();
          }
          last_x = x;
          last_y = y;
          last_z = z;
              
          
          if(toWork){
          //here should be the logic to do when shake is off     
         if(speed>=SHAKE_THRESHOLD){
        	 if(sleepFlag){//we slept
        		 //Toast.makeText(getApplicationContext(), "we waked up in onSensor", Toast.LENGTH_SHORT).show();
        		 sleepFlag=false;// we are belived to wake up
        		 counter=0;
        		 start=0;//set ring off 
        	 }
        	 else{// we were awake
        		 //Toast.makeText(getApplicationContext(), "we were wake in onSensor", Toast.LENGTH_SHORT).show();
        		 counter=0;//move to next counting stage
        		 this.idx++;//move to next counting stage
        		 Toast.makeText(getApplicationContext(), "stage is now = "+(checkInterval+idx), Toast.LENGTH_SHORT).show();
        	 }
        		 
        	 MediaSingleton.INSTANCE.setVolume(start);//update sound
         }
          }//toWork end     
      }
  }

  @Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored for this demo
	}
  
  
  
  /**
   * Helper class for gradually increasing the volume of the alarm audio
   * stream.
   */
  private final class VolumeIncreaser implements Runnable {
    float end;
    float increment;

    public float volume() {
      return start;
    }

    public void reset(AlarmSettings settings) {
      start = (float) (settings.getVolumeStartPercent() / 100.0)+5*jdx;
      end = (float) (settings.getVolumeEndPercent() / 100.0);
      increment = (end - start) / settings.getVolumeChangeTimeSec();
    }
    
    
    
    @Override
    public void run() {
    	
    	if(toWork){
    	//here should be the logic to do when shake is off
    	if(sleepFlag){
    		//Toast.makeText(getApplicationContext(), "we are asleep at run", Toast.LENGTH_SHORT);
    		counterTotal = 0;//get back to start
    		
    		if(idx==0){
    			jdx++;//should increase startVol
    			
    			AlarmSettings settings=null;
				try {
					settings = db.readAlarmSettings(currentAlarmId());
				} catch (NoAlarmsException e) {
					e.printStackTrace();
				}
				
    			reset(settings);
    		}
    		
	      start += increment;//we are asleep -> so we should increase volume
	      
	      if (start > end) {
	        start = end;// don't reach too high volume
	      }
	     
	      MediaSingleton.INSTANCE.setVolume(start);//update sound
    	}
    	
    	
    	else{
    		//Toast.makeText(getApplicationContext(), "we are awake at run", Toast.LENGTH_SHORT).show();
    		//we are believe to be awake
    		counter++;//adds sec
    		counterTotal++;//adds sec

    		//counter ==x-> we have not moved for x secs
    		if(counter == checkInterval+idx){//should be a recursive sum
    			//Toast.makeText(getApplicationContext(), "we have not moved for "+(checkInterval+idx)+ " at run", Toast.LENGTH_SHORT).show();
    			//if we have not moved for checkIntreval Secs we should restart the counting
    			clock_text_g = "You went back to sleep didn't you?";
    			sleepFlag = true;
    			counterTotal = 0;
    			counter = 0;
    			idx=0;
    		}

    	}
    	if (counterTotal != timeToShake) {
    		handler.postDelayed(volumeIncreaseCallback, 700);//what delay to set?
    	}
    	else {
    		counterTotal = counter = 0;
    		sleepFlag = true;
    		idx=0;
    		toWork=false;
    		
    		//Toast.makeText(getBaseContext(), "you are awake", Toast.LENGTH_SHORT).show();
    		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("finish_code", "false").commit();
    		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("finish_code", "true").commit();
    		
    		String st=PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("finish_code", "false"); 
    		
    		
    		
    	}
    	}//toWork end
      
    }
  }
}
