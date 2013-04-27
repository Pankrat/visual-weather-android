package com.visualweather;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ForecastActivity extends FragmentActivity {

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;

	double latitude;
	double longitude;

	private JSONArray weatherForecast;
	private long lastUpdate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_forecast);
		lastUpdate = 0;
	}	
	
	@Override
	protected void onResume() {
		super.onResume();
		Date now = new Date();
		if (lastUpdate == 0 || now.getTime() - lastUpdate > 1000*60*60) {
			// Get last known location
			LocationManager locationManager = (LocationManager) this
					.getSystemService(Context.LOCATION_SERVICE);
			String locationProvider = LocationManager.NETWORK_PROVIDER;
			Location lastKnownLocation = locationManager
					.getLastKnownLocation(locationProvider);
			latitude = lastKnownLocation.getLatitude();
			longitude = lastKnownLocation.getLongitude();

			// Reverse geo-coding (latitude/longitude to city)
			Geocoder geo = new Geocoder(this);
			try {
				List<Address> addresses = geo.getFromLocation(latitude, longitude,
						1);
				if (addresses != null && addresses.size() > 0) {
					Address address = addresses.get(0);
					if (address.getSubLocality() != null) {
						setTitle(address.getSubLocality());
					} else if (address.getLocality() != null) {
						setTitle(address.getLocality());
					}
				}
			} catch (IOException e) {
				Log.e("LOCATION", "Impossible to connect to Geocoder", e);
			}
			GetWeatherTask task = new GetWeatherTask();
			task.execute(latitude, longitude);
			lastUpdate = now.getTime();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.forecast, menu);
		return true;
	}

	private class GetWeatherTask extends AsyncTask<Double, Void, JSONArray> {

		private JSONObject getJson(URL url) {
			StringBuilder response = new StringBuilder();
			HttpURLConnection urlConnection = null;
			try {
				urlConnection = (HttpURLConnection) url.openConnection();
				InputStream in = new BufferedInputStream(
						urlConnection.getInputStream());
				BufferedReader streamReader = new BufferedReader(
						new InputStreamReader(in, "UTF-8"));

				String inputStr;
				while ((inputStr = streamReader.readLine()) != null)
					response.append(inputStr);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				urlConnection.disconnect();
			}
			try {
				return new JSONObject(response.toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return new JSONObject();
			}
		}

		@Override
		protected JSONArray doInBackground(Double... params) {
			double latitude = params[0];
			double longitude = params[1];
			JSONArray forecast = null;
			try {
				URL url = new URL(
						String.format(
								"http://api.openweathermap.org/data/2.1/find/city?lat=%.6f&lon=%.6f&cnt=1",
								latitude, longitude));
				JSONObject json = getJson(url);
				JSONObject station = json.getJSONArray("list").getJSONObject(0);
				int station_id = station.getInt("id");
				url = new URL(
						String.format(
								"http://api.openweathermap.org/data/2.2/forecast/city/%s?mode=daily_compact&units=metric",
								station_id));
				json = getJson(url);
				forecast = json.getJSONArray("list");
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.i("WEATHER", "Update weather data");
			return forecast;
		}

		@Override
		protected void onPostExecute(JSONArray result) {
			weatherForecast = result;
			
			mSectionsPagerAdapter = new SectionsPagerAdapter(
					getSupportFragmentManager());

			// Set up the ViewPager with the sections adapter.
			mViewPager = (ViewPager) findViewById(R.id.pager);
			mViewPager.setAdapter(mSectionsPagerAdapter);
		}
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			// Return a DummySectionFragment (defined as a static inner class
			// below) with the page number as its lone argument.
			Fragment fragment = new ForecastFragment();
			Bundle args = new Bundle();
			args.putInt(ForecastFragment.ARG_SECTION_NUMBER, position + 1);
			try {
				args.putString(ForecastFragment.ARG_FORECAST,
							weatherForecast.get(position).toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public int getCount() {
			return 5;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, position);
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL,
					Locale.getDefault());
			return dateFormat.format(cal.getTime());
		}
	}

	/**
	 * A dummy fragment representing a section of the app, but that simply
	 * displays dummy text.
	 */
	public static class ForecastFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";
		public static final String ARG_FORECAST = "forecast";

		private TextView textView;

		public ForecastFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_forecast_dummy,
					container, false);
			textView = (TextView) rootView.findViewById(R.id.section_label);
			TextView tempView = (TextView) rootView.findViewById(R.id.temperature);
			String weather = getArguments().getString(ARG_FORECAST);
			try {
				JSONObject json = new JSONObject(weather);
				long timestamp = json.getLong("dt");
				Date date = new Date();
				date.setTime(timestamp * 1000);
				textView.setText(date.toString() + "\n" + weather);
				Double temp = json.getDouble("temp");
				tempView.setText(Integer.toString((int)Math.round(temp)) + "°C");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return rootView;
		}

	}
}
