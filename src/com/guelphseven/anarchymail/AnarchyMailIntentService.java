/*
 * AutoAnswer
 * Copyright (C) 2010 EverySoft
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *   Copyright (C) 2010 Tedd Scofield
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.guelphseven.anarchymail;

import java.lang.reflect.Method;
import com.android.internal.telephony.ITelephony;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaRecorder.AudioSource;
import android.media.MediaRecorder.OutputFormat;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.text.format.Time;
import java.io.File;
import java.lang.Thread;


public class AnarchyMailIntentService extends IntentService {

	public AnarchyMailIntentService() {
		super("AutoAnswerIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Context context = getBaseContext();

		// Load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		BluetoothHeadset bh = null;
		if (prefs.getBoolean("headset_only", false)) {
			bh = new BluetoothHeadset(this, null);
		}

		// Let the phone ring for a set delay
		try {
			Thread.sleep(Integer.parseInt(prefs.getString("delay", "2")) * 1000);
		} catch (InterruptedException e) {
			// We don't really care
		}

		// Check headset status right before picking up the call
		if (prefs.getBoolean("headset_only", false) && bh != null) {
			if (bh.getState() != BluetoothHeadset.STATE_CONNECTED) {
				bh.close();
				return;
			}
			bh.close();
		}

		// Make sure the phone is still ringing
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (tm.getCallState() != TelephonyManager.CALL_STATE_RINGING) {
			return;
		}

		// Answer the phone
		try {
			answerPhoneAidl(context);
		}
		catch (Exception e) {
			e.printStackTrace();
			Log.d("AutoAnswer","Error trying to answer using telephony service.  Falling back to headset.");
			answerPhoneHeadsethook(context);
		}

		AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

		MediaPlayer player = new MediaPlayer();
		player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
		try{player.setDataSource("/sdcard/Voicemail/a.message/audiofile.mp3");}catch(Exception e){}

		manager.setSpeakerphoneOn(true);
		try{player.prepare();}catch(Exception e){}
		player.start();

		while(player.isPlaying());
		player.stop();
		player.reset();   // You can reuse the object by going back to setAudioSource() step
		player.release(); // Now the object cannot be reused
		manager.setSpeakerphoneOn(false);

		MediaRecorder recorder = new MediaRecorder();
		recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK);
		recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		String saveLocation="/sdcard/Voicemail/";

		int waitTime = Integer.parseInt(prefs.getString("message_length", "30"));
		int s=0, ls=0, elapsed = 0;
		Time t = new Time();
		t.setToNow();

		File folder=new File(saveLocation);
		if (!folder.exists())
		{
			if (!folder.mkdir()){}
		}

		String ampm = (t.hour < 12) ? " AM" : " PM";
		String hours;
		String minutes;
		
		if(t.hour%12 < 10) hours = "0"+t.hour%12;
		else hours = Integer.toString(t.hour%12);
		
		if(t.minute < 10) minutes = "0"+t.minute;
		else hours = Integer.toString(t.minute);
		
		recorder.setOutputFile(saveLocation + 
				hours + t.minute + ampm + ", " +
				t.monthDay + "-" + t.month + "-" + t.year + ".3gp");

		try{
			recorder.prepare();
		} catch(Exception e){
			System.out.println("You are fail. Goodbye!");
			// we don't care!
		}

		recorder.start();
		while((tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) && elapsed < waitTime)
		{
			t.setToNow();
			s = t.second;
			if(s != ls)
			{
				elapsed++;
				ls = s;
			}
		}

		try{
			recorder.stop();
			recorder.reset();   // You can reuse the object by going back to setAudioSource() step
			recorder.release(); // Now the object cannot be reused

		}catch(Exception e){
			System.out.println("Second fail.");
		}

		if(tm.getCallState() != TelephonyManager.CALL_STATE_IDLE)	
		{
			// toggle airplane mode
			Settings.System.putInt( context.getContentResolver(),
					Settings.System.AIRPLANE_MODE_ON, 1);

			// Post an intent to reload
			Intent intent1 = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
			intent1.putExtra("state", 1);
			sendBroadcast(intent1);

			try{Thread.sleep(4000);}catch(Exception e){}

			Settings.System.putInt( context.getContentResolver(),
					Settings.System.AIRPLANE_MODE_ON, 0);

			// Post an intent to reload
			intent1 = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
			intent1.putExtra("state", 0);
			sendBroadcast(intent1);
		}

		// Enable the speaker phone
		if (prefs.getBoolean("use_speakerphone", false)) {
			enableSpeakerPhone(context);
		}
		return;
	}

	private void enableSpeakerPhone(Context context) {
		AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		audioManager.setSpeakerphoneOn(true);
	}

	private void answerPhoneHeadsethook(Context context) {
		// Simulate a press of the headset button to pick up the call
		Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);		
		buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
		context.sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");

		// froyo and beyond trigger on buttonUp instead of buttonDown
		Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);		
		buttonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
		context.sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
	}

	@SuppressWarnings("unchecked")
	private void answerPhoneAidl(Context context) throws Exception {
		// Set up communication with the telephony service (thanks to Tedd's Droid Tools!)
		TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		Class c = Class.forName(tm.getClass().getName());
		Method m = c.getDeclaredMethod("getITelephony");
		m.setAccessible(true);
		ITelephony telephonyService;
		telephonyService = (ITelephony)m.invoke(tm);

		// Silence the ringer and answer the call!
		telephonyService.silenceRinger();
		telephonyService.answerRingingCall();
	}


}
