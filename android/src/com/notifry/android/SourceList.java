/**
 * Notifry for Android.
 * 
 * Copyright 2011 Daniel Foote
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * The contents of this are heavily based on this blog post:
 * http://blog.notdot.net/2010/05/Authenticating-against-App-Engine-from-an-Android-app
 * Thanks dude for your awesome writeup!
 */

package com.notifry.android;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import com.notifry.android.database.NotifryAccount;
import com.notifry.android.database.NotifryDatabaseAdapter;
import com.notifry.android.database.NotifrySource;
import com.notifry.android.remote.BackendRequest;
import com.notifry.android.remote.BackendResponse;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SourceList extends ListActivity
{
	public final static int ADD_SOURCE = 1;
	private static final String TAG = "Notifry";
	private final SourceList thisActivity = this;
	private NotifryAccount account = null;

	/** Called when the activity is first created. */
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate(savedInstanceState);

		// Set the layout, and allow text filtering.
		setContentView(R.layout.screen_sources);
		getListView().setTextFilterEnabled(true);
		
		// Sync the list off the server.
		// TODO: Handle this gracefully - you might want to use this simply
		// to quickly disable a source locally.
		BackendRequest request = new BackendRequest("/sources/list");

		// Indicate what we're doing.
		request.addMeta("operation", "list");

		// For debugging, dump the request data.
		//request.dumpRequest();
		
		// Where to come back when we're done.
		request.setHandler(handler);

		// Start a thread to make the request.
		// This will just update our view when ready.
		request.startInThread(this, null, this.getAccount().getAccountName());		
	}

	public void onResume()
	{
		super.onResume();

		// When coming back, refresh our list of accounts.
		refreshView();
	}

	/**
	 * Fetch the account that this source list is for.
	 * 
	 * @return
	 */
	public NotifryAccount getAccount()
	{
		if( this.account == null )
		{
			// Get the account from the intent.
			// We store it in a private variable to save us having to query the
			// DB each time.
			Intent sourceIntent = getIntent();
			NotifryDatabaseAdapter database = new NotifryDatabaseAdapter(this);
			database.open();
			this.account = database.getAccountByName(sourceIntent.getStringExtra("account"));
			database.close();
		}

		return this.account;
	}

	/**
	 * Refresh the list of sources viewed by this activity.
	 */
	public void refreshView()
	{
		// Refresh our list of sources.
		NotifryDatabaseAdapter database = new NotifryDatabaseAdapter(this);
		database.open();
		ArrayList<NotifrySource> sources = database.listSources(this.getAccount().getAccountName());
		database.close();

		this.setListAdapter(new SourceArrayAdapter(this, this, R.layout.source_list_row, sources));
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, ADD_SOURCE, 0, R.string.create_source).setIcon(android.R.drawable.ic_menu_add);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		switch( item.getItemId() )
		{
			case ADD_SOURCE:
				askForSourceName();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Helper function to show a dialog to ask for a source name.
	 */
	private void askForSourceName()
	{
		final EditText input = new EditText(this);

		new AlertDialog.Builder(this).
				setTitle(getString(R.string.create_source)).
				setMessage(getString(R.string.create_source_message)).
				setView(input).
				setPositiveButton(
						getString(R.string.create),
						new DialogInterface.OnClickListener()
						{
							public void onClick( DialogInterface dialog, int whichButton )
							{
								Editable value = input.getText();
								if( value.length() > 0 )
								{
									// Fire it off to the create source function.
									createSource(value.toString());
								}
							}
						}).
				setNegativeButton(
						getString(R.string.cancel),
						new DialogInterface.OnClickListener()
						{
							public void onClick( DialogInterface dialog, int whichButton )
							{
								// No need to take any action.
							}
						}).
				show();
	}
	
	/**
	 * Helper function to create a source.
	 * @param title
	 */
	public void createSource( String title )
	{
		// We need to send this request to the backend, and then it will set up everything we need.
		BackendRequest request = new BackendRequest("/sources/create");
		request.add("title", title);
		request.add("enabled", "on");

		// Indicate what we're doing.
		request.addMeta("operation", "create");

		// For debugging, dump the request data.
		//request.dumpRequest();
		
		// Where to come back when we're done.
		request.setHandler(handler);

		// Start a thread to make the request.
		request.startInThread(this, getString(R.string.create_source_server_waiting), this.getAccount().getAccountName());
	}

	/**
	 * Handler for when you click an source.
	 * 
	 * @param account
	 */
	public void clickSource( NotifrySource source )
	{
		// Launch the source editor.
		Intent intent = new Intent(getBaseContext(), SourceEditor.class);
		intent.putExtra("sourceId", source.getId());
		startActivity(intent);
	}
	
	/**
	 * Handler for when you change a source's enabled status.
	 * @param source
	 * @param state
	 */
	public void checkedSource( NotifrySource source, boolean state )
	{
		// All we're doing is changing the LOCAL enable flag.
		// Refresh the source.
		NotifryDatabaseAdapter database = new NotifryDatabaseAdapter(this);
		database.open();
		NotifrySource refreshedSource = database.getSourceById(source.getId());
		refreshedSource.setLocalEnabled(state);
		database.saveSource(refreshedSource);
		database.close();
		
		// And refresh our view.
		refreshView();
	}

	/**
	 * Private handler class that is the callback for when the external requests
	 * are complete.
	 */
	private Handler handler = new Handler()
	{
		@Override
		public void handleMessage( Message msg )
		{
			// Fetch out the response.
			BackendResponse response = (BackendResponse) msg.obj;

			// Was it successful?
			if( response.isError() )
			{
				// No, not successful.
				Toast.makeText(thisActivity, response.getError() + " - Please try again.", Toast.LENGTH_LONG).show();
			}
			else
			{
				try
				{
					// Fetch out metadata.
					BackendRequest request = response.getRequest();
					String operation = (String) request.getMeta("operation");

					// Determine our operation.
					if( operation.equals("create") )
					{
						// We were creating a new source.
						// The server would have given us a complete source object.
						NotifrySource source = new NotifrySource();
						source.fromJSONObject(response.getJSON().getJSONObject("source"));
						source.setAccountName(getAccount().getAccountName());
						source.setLocalEnabled(true); // Enabled by default.

						// Open the database and save it.
						NotifryDatabaseAdapter database = new NotifryDatabaseAdapter(thisActivity);
						database.open();
						database.saveSource(source);
						database.close();

						refreshView();

						Toast.makeText(thisActivity, getString(R.string.create_source_server_complete), Toast.LENGTH_SHORT).show();
					}
					else if( operation.equals("list") )
					{
						// We just got a list from the server. Sync it up!
						JSONArray serverList = response.getJSON().getJSONArray("sources");
						NotifrySource.syncFromJSONArray(thisActivity, serverList, thisActivity.getAccount().getAccountName());
						
						// And refresh.
						refreshView();
					}
				}
				catch( JSONException e )
				{
					// The response doesn't look like we expected.
					Log.d(TAG, "Invalid response from server: " + e.getMessage());
					Toast.makeText(thisActivity, "Invalid response from the server.", Toast.LENGTH_LONG).show();
					refreshView();
				}
			}
		}
	};

	/**
	 * An array adapter to put sources into the list view.
	 * 
	 * @author daniel
	 */
	private class SourceArrayAdapter extends ArrayAdapter<NotifrySource>
	{
		final private SourceList parentActivity;
		private ArrayList<NotifrySource> sources;

		public SourceArrayAdapter( SourceList parentActivity, Context context, int textViewResourceId, ArrayList<NotifrySource> objects )
		{
			super(context, textViewResourceId, objects);
			this.parentActivity = parentActivity;
			this.sources = objects;
		}

		public View getView( int position, View convertView, ViewGroup parent )
		{
			// Inflate a view if required.
			if( convertView == null )
			{
				LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(R.layout.source_list_row, null);
			}

			// Find the account.
			final NotifrySource source = this.sources.get(position);

			// And set the values on our row.
			if( source != null )
			{
				TextView title = (TextView) convertView.findViewById(R.id.source_row_source_name);
				TextView serverEnabled = (TextView) convertView.findViewById(R.id.source_row_server_enabled);
				CheckBox enabled = (CheckBox) convertView.findViewById(R.id.source_row_local_enabled);
				
				View.OnClickListener clickListener = new View.OnClickListener()
				{
					public void onClick( View v )
					{
						parentActivity.clickSource(source);
					}
				};
				
				if( title != null )
				{
					title.setText(source.getTitle());
					title.setClickable(true);

					title.setOnClickListener(clickListener);
				}
				if( serverEnabled != null )
				{
					serverEnabled.setClickable(true);
					if( source.getServerEnabled() == false )
					{
						serverEnabled.setText(getString(R.string.source_disabled_on_server));
					}
					else
					{
						serverEnabled.setText("");
					}
					serverEnabled.setOnClickListener(clickListener);
				}
				if( enabled != null )
				{
					enabled.setChecked(source.getLocalEnabled());

					// This doesn't seem memory friendly, but we'll get away
					// with it because
					// there won't be many registered sources.
					enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
					{
						public void onCheckedChanged( CompoundButton buttonView, boolean isChecked )
						{
							parentActivity.checkedSource(source, isChecked);
						}
					});
				}
			}

			return convertView;
		}
	}
}