package com.coderbloc.aparnasridhar.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * Created by aparnasridhar on 4/14/15.
 */
public class ForecastFragement extends Fragment {

    ArrayAdapter<String> adapter;

    public ForecastFragement() {
    }

    @Override
    public void onStart()
    {
        super.onStart();
        updateWeatherData();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if( id == R.id.action_refresh){

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeatherData()
    {
        FetchWeatherTask task = new FetchWeatherTask();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = pref.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        task.execute(location);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        adapter = new ArrayAdapter<String>
                (
                        //Context
                        getActivity(),
                        //ID of list item layout
                        R.layout.list_item_layout,
                        //Id of textview to populate
                        R.id.list_item_forecast_textview,
                        //Forecast data
                        new ArrayList<String>());
        final View rootView = inflater.inflate(R.layout.fragment_main, container, false);


        ListView view = (ListView) rootView.findViewById(R.id.listview_forecast);
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(getActivity(),adapter.getItem(position), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(),DetailActivity.class).putExtra(Intent.EXTRA_TEXT,adapter.getItem(position));
                startActivity(intent);
            }
        });
        view.setAdapter(adapter);


        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        /* The date/time conversion code is going to be moved outside the asynctask later,
        * so for convenience we're breaking it out into its own method now.
        */
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        @Override
        protected void onPostExecute(String[] strings) {


            if(strings!=null) {
                //Log.v("Data",strings.toString());
                adapter.clear();
                for(String forecast:strings)
                {
                    adapter.add(forecast);
                }
                //adapter.notifyDataSetChanged();
            }
        }


        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low, String unitType) {

            if (unitType.equals("imperial")) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals("metric")) {
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }

            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }
        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_temperature_key),
                    getString(R.string.pref_temperature_default));

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low,unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        @Override
        protected String[] doInBackground(String... urls) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {

                if(urls.length==0) {
                    return null;
                }
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast

                String FORECAST_BASE_URL="http://api.openweathermap.org/data/2.5/forecast/daily?";
                String QUERY_PARAM = "q";
                String FORMAT_MODE="mode";
                String UNIT_PARAMS = "units";
                String DAYS_PARAM = "cnt";
                String format = "json";
                String units = "metric";
                int numOfDays = 7;

                Uri buildUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,urls[0])
                        .appendQueryParameter(FORMAT_MODE,format)
                        .appendQueryParameter(UNIT_PARAMS,units)
                        .appendQueryParameter(DAYS_PARAM,Integer.toString(numOfDays)).build();

                URL url = new URL(buildUri.toString());

                Log.v("URI",buildUri.toString());


                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();
                try {
                    return getWeatherDataFromJson(forecastJsonStr, numOfDays);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                    e.printStackTrace();
                }

                Log.v("placeholderfragment","we did it");
            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("ForecastFragment", "Error closing stream", e);
                    }
                }
            }

            return null;
        }

    }

}