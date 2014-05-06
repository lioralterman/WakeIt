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

import java.io.Serializable;


import com.wakeit.NotificationServiceInterface;
import com.wakeit.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the activity responsible for alerting the user when an alarm goes
 * off.  It is the activity triggered by the NotificationService.  It assumes
 * that the intent sender has acquired a screen wake lock.
 * NOTE: This class assumes that it will never be instantiated nor active
 * more than once at the same time. (ie, it assumes
 * android:launchMode="singleInstance" is set in the manifest file).
 */
public final class ActivityAlarmNotification extends Activity {
  public final static String TIMEOUT_COMMAND = "timeout";
  private enum Dialogs { TIMEOUT }

  private OnSharedPreferenceChangeListener listener;
  private NotificationServiceBinder notifyService;
  private DbAccessor db;
  private Handler handler;
  private Runnable timeTick;
  private Typeface tf, tf1;

  // Dialog state
  int snoozeMinutes;
  
  

  
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.notification);
    
    // Make sure this window always shows over the lock screen.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    tf = Typeface.createFromAsset(getAssets(),"fonts/DS-DIGI.TTF");
    tf1 = Typeface.createFromAsset(getAssets(),"fonts/OstrichSans-Bold.otf");
    db = new DbAccessor(getApplicationContext());
    
    Button terBtn = (Button) findViewById(R.id.terminator);
    terBtn.setText("EXIT");
    terBtn.setOnClickListener(new Button.OnClickListener() {

        @Override
        public void onClick(View v) {
        	notifyService.acknowledgeCurrentNotification(0);  
      		finish();
         }
        });
    if(AppSettings.isTerButtonOn(getApplicationContext())){
    	terBtn.setVisibility(View.VISIBLE);
    	terBtn.setBackgroundColor(Color.RED);
    	terBtn.setEnabled(true);
    }else{
    	terBtn.setVisibility(View.VISIBLE);
    	terBtn.setBackgroundColor(Color.TRANSPARENT);
    	terBtn.setTextColor(Color.TRANSPARENT);
    	terBtn.setEnabled(false);
    }
    
    
    // Start the notification service and bind to it.
    notifyService = new NotificationServiceBinder(getApplicationContext());
    notifyService.bind();

    // Setup a self-scheduling event loops.
    handler = new Handler();
    
    
    SharedPreferences prefs = 
    	    PreferenceManager.getDefaultSharedPreferences(this);
    
    listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
    	  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {																																																																																																																																																							 
      		//Toast.makeText(getBaseContext(), "I'M INSIDE THE LISTENER", Toast.LENGTH_SHORT).show();
      		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("finish_code", "false").commit();
      		notifyService.acknowledgeCurrentNotification(0);  
      		finish();
    	  }
    	};

	prefs.registerOnSharedPreferenceChangeListener(listener);

    timeTick = new Runnable() {
      @Override
      public void run() {
        notifyService.call(new NotificationServiceBinder.ServiceCallback() {
          @Override
          public void run(NotificationServiceInterface service) {
            /* try {
              TextView volume = (TextView) findViewById(R.id.volume);
              volume.setText("Volume: " + service.volume()); 
            } catch (RemoteException e) {} */

        	  TextView countdown = (TextView) findViewById(R.id.alarm_countdown);
			  countdown.setText("" + (NotificationService.timeToShake - NotificationService.counterTotal));
			  countdown.setTypeface(tf);
			  TextView alarm_text = (TextView) findViewById(R.id.alarm_text);
			  alarm_text.setText(NotificationService.clock_text_g);
			  alarm_text.setTypeface(tf1);
			  
			  TextView clock = (TextView) findViewById(R.id.clock);
			  clock.setTypeface(tf1);
			  
			  
            long next = AlarmUtil.millisTillNextInterval(AlarmUtil.Interval.SECOND);
            handler.postDelayed(timeTick, next);
          }
        });
      }
    };
    
    
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    handler.post(timeTick);
    redraw();
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacks(timeTick);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    db.closeConnections();
    notifyService.unbind();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Bundle extras = intent.getExtras();
    if (extras == null || extras.getBoolean(TIMEOUT_COMMAND, false) == false) {
      return;
    }
    // The notification service has signaled this activity for a second time.
    // This represents a acknowledgment timeout.  Display the appropriate error.
    // (which also finish()es this activity.
    showDialog(Dialogs.TIMEOUT.ordinal());
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (Dialogs.values()[id]) {
      case TIMEOUT:
        final AlertDialog.Builder timeoutBuilder = new AlertDialog.Builder(this);
        timeoutBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        timeoutBuilder.setTitle(R.string.time_out_title);
        timeoutBuilder.setMessage(R.string.time_out_error);
        timeoutBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
          @Override
          public void onClick(DialogInterface dialog, int which) {}
        });
        AlertDialog dialog = timeoutBuilder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            finish();
          }});
        return dialog;
      default:
        return super.onCreateDialog(id);
    }
  }

  private final void redraw() {
    notifyService.call(new NotificationServiceBinder.ServiceCallback() {
      @Override
      public void run(NotificationServiceInterface service) {
        long alarmId;
        try {
          alarmId = service.currentAlarmId();
        } catch (RemoteException e) {
          return;
        }
        AlarmInfo alarmInfo = db.readAlarmInfo(alarmId);

        String info = alarmInfo.getTime().toString() + "\n" + alarmInfo.getName();
        if (AppSettings.isDebugMode(getApplicationContext())) {
          info += " [" + alarmId + "]";
          //findViewById(R.id.volume).setVisibility(View.VISIBLE);
        } else {
          //findViewById(R.id.volume).setVisibility(View.GONE);
        }
        //TextView infoText = (TextView) findViewById(R.id.alarm_info);
        //infoText.setText(info);
       /* TextView snoozeInfo = (TextView) findViewById(R.id.notify_snooze_time);
        snoozeInfo.setText(getString(R.string.snooze) + "\n"
            + getString(R.string.minutes, snoozeMinutes)); */
      }
    });
  }



	

}
