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
 */

package com.guelphseven.anarchymail;

import java.io.File;

import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AnarchyMailFileDisplay extends ListActivity{

	//private AnarchyMailFileDisplay mFileDisplay;
	private SharedPreferences mSharedPreferences;
	private Context mContext;
	private boolean lock;
	/*public AnarchyMailFileDisplay(Context context) {
		mContext = context;
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
	}*/

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Environment.getExternalStorageDirectory().
		//File file[] = Environment.getExternalStorageDirectory().listFiles();
		File mail_dir = new File("/sdcard/Voicemail");
		final int length;
		String temp = new String();
		int j = 0;
		
		if(mail_dir != null)
		{
			File file[] = mail_dir.listFiles();
			
			String[] array = new String[file.length];
			/*for(int i = 0; i < file.length; i++)
			{
				String[] fail = file[i].getName().split("\\.");
				array[i] = fail[0];
				for(int j = 1; j < fail.length; j++)
				{
					array[i] += ";" + fail[j];
				}
				//array[i] = file[i].getName();
			}*/
			
			int seconds, minutes, time;
			String seconds_str, minutes_str;			

			for(int i = 0; i < file.length; i++)
			{
				//Toast.makeText(getApplicationContext(), file[i].getName(), Toast.LENGTH_SHORT);
				if(file[i].getName().contains(".3gp"))
				{					
					MediaPlayer p = new MediaPlayer();
					//p.setAudioStreamType(AudioManager.STREAM_MUSIC);
					array[j] = file[i].getName().split("\\.")[0];
					p.setAudioStreamType(AudioManager.STREAM_MUSIC);
					try{p.prepare();}catch(Exception e){}
					try{p.setDataSource("/sdcard/Voicemail/" + array[j] + ".3gp");}catch(Exception e)
					{
						System.out.println("ohno");
					}
					
					time = p.getDuration()/1000;
					p.release();
					seconds = time % 60;
					minutes = time / 60;
					
					if(minutes < 10)
						minutes_str = "0" + minutes;//Integer.toString(minutes);
					else
						minutes_str = Integer.toString(minutes);
					if(seconds < 10)
						seconds_str = "0" + seconds;//Integer.toString(seconds);
					else
						seconds_str = Integer.toString(seconds);
					
					//array[j] = minutes_str + ":" + seconds_str + " -- " + array[j];
					array[j] = time + " -- " + array[j];
					
					//array[j] = temp.toString();
					j += 1;
				}
				else
				{
					//array[j] = "error";
					//j += 1;
				}
			}
			String[] array2 = new String[j];
			for(int i = 0; i < j; i++)
			{
				array2[i] = array[i];
			}

			setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, array2));
			//getListView().invalidateViews();

			ListView lv = getListView();
			lv.setTextFilterEnabled(true);

			final Toast finished = Toast.makeText(getApplicationContext(), "Finished", Toast.LENGTH_SHORT);
			final Toast please_wait = Toast.makeText(getApplicationContext(), "Playback in progress, please wait.", Toast.LENGTH_SHORT);
			
			lv.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					// When clicked, show a toast with the TextView text
					if(lock == false)
					{
						String toplay;
						lock = true;				

						final MediaPlayer player = new MediaPlayer();
						player.setAudioStreamType(AudioManager.STREAM_MUSIC);
						toplay = ((TextView) view).getText().toString().split("--")[1].trim();
						try{player.setDataSource("/sdcard/Voicemail/" + toplay + ".3gp");}catch(Exception e){}
						Toast.makeText(getApplicationContext(), toplay, Toast.LENGTH_SHORT).show();
							
						Thread thread = new Thread() {
							public void run()
							{
								try{player.prepare();}catch(Exception e){}
								player.start();

								while(player.isPlaying());
								player.stop();
								player.reset();   // You can reuse the object by going back to setAudioSource() step
								player.release();

								finished.show();
								lock = false;
							}
						};
						thread.start();
					}
					else
					{
						please_wait.show();
					}
				}
			});
		}
	}
}