package rs.pedjaapps.Linpack;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.meicorl.unikernel.lib.ControlMessages;
import org.meicorl.unikernel.lib.ExecutionController;

public class MainActivity extends Activity implements Runnable, OnSharedPreferenceChangeListener
{
    TextView mflopsTextView;
    TextView nresTextView;
    TextView timeTextView;
    TextView precisionTextView;
    ListView resultsList;
    ResultsListAdapter adapter;
    DatabaseHandler db;
    Button start_single;
    Handler linpackHandler;
    Handler uiHandler;

    private long startTime;

    final DecimalFormat mflopsFormat = new DecimalFormat("0.000");
    final DecimalFormat nResFormat = new DecimalFormat("0.00");
    SimpleDateFormat f = new SimpleDateFormat("dd MMM yy HH:mm:ss");
	
	SharedPreferences settings = null;
	
    private static String TAG = "LinpackMainActivity";

    //Members for Offloading framework
	private ExecutionController executionController = null;

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        HandlerThread thread = new HandlerThread("linpack");
        thread.start();
        linpackHandler = new Handler(thread.getLooper());
        uiHandler = new Handler();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);

        // I wanna use network in main thread, so ...
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        mflopsTextView = (TextView) findViewById(R.id.mflops);
        nresTextView = (TextView) findViewById(R.id.nres);
        timeTextView = (TextView) findViewById(R.id.time);
        precisionTextView = (TextView) findViewById(R.id.precision);

        start_single = (Button) findViewById(R.id.start_single);
        start_single.setOnClickListener(new View.OnClickListener() {
            public void onClick(View p1) {
                mflopsTextView.setText(R.string.running_benchmark);
                nresTextView.setText("0");
                timeTextView.setText("0");
                precisionTextView.setText("0");
                start_single.setEnabled(false);
                startLinpack();
            }
        });
        resultsList = (ListView) findViewById(R.id.list);
        adapter = new ResultsListAdapter(this, R.layout.results_row);
        resultsList.setAdapter(adapter);
        db = new DatabaseHandler(this);
        populateList();
        resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.result_dialog_layout, null);
                TextView mflopsTextView = (TextView) view.findViewById(R.id.mflops);
                TextView nresTextView = (TextView) view.findViewById(R.id.nres);
                TextView timeTextView = (TextView) view.findViewById(R.id.time);
                TextView precisionTextView = (TextView) view.findViewById(R.id.precision);
                Result e = db.getResultByDate(adapter.getItem(position).date);

                mflopsTextView.setText(e.mflops + "");
                if (e.mflops < 30) {
                    mflopsTextView.setTextColor(Color.RED);
                } else {
                    mflopsTextView.setTextColor(Color.GREEN);
                }
                nresTextView.setText(e.nres + "");
                if (e.nres > 5) {
                    nresTextView.setTextColor(Color.YELLOW);
                } else if (e.nres > 10) {
                    nresTextView.setTextColor(Color.RED);
                } else {
                    nresTextView.setTextColor(Color.GREEN);
                }
                timeTextView.setText(e.time / 1000 + "s");
                precisionTextView.setText("" + e.precision);

                builder.setNegativeButton(getResources().getString(R.string.close), null);

                builder.setView(view);
                AlertDialog alert = builder.create();
                alert.show();
            }
        });

        // Create an execution controller
        this.executionController = new ExecutionController(this.getPackageName(), this.getPackageManager(), this);
        readPrefs();
    }

    private void readPrefs() {
        boolean alwaysLocal = settings.getBoolean("alwaysLocal", false);
        Log.d(TAG, "alwaysLocal is " + alwaysLocal);
        if(alwaysLocal){
        	this.executionController.setUserChoice(ControlMessages.STATIC_LOCAL);
        }else{
        	this.executionController.setUserChoice(ControlMessages.USER_CARES_ONLY_ENERGY);
        }
    }
    
    private void startLinpack()
    {
        linpackHandler.post(this);
    }

    private void populateList()
    {
        adapter.clear();
        List<Result> results = db.getAllResults();
        Collections.reverse(results);
        for(Result r : results)
        {
            adapter.add(r);
        }
        adapter.notifyDataSetChanged();
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        this.executionController.releaseConnection();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
        	Intent mIntent = new Intent();  
            mIntent.setClass(this, Preferences.class);  
            startActivity(mIntent);  
            break;
        }
        return super.onOptionsItemSelected(item);  
    }

    @Override
    public void run()
    {
        startTime = System.currentTimeMillis();
        Linpack lp = new Linpack(this.executionController);

        int[] baseArray = {7, 25, 15, 4, 1, 10, 14, 24, 2, 22, 5, 23, 11, 18, 
        		20, 13, 6, 17, 3, 19, 16, 9, 12, 8, 21};

        final Result result = lp.doLinpack(baseArray);
        uiHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                result.time = System.currentTimeMillis() - startTime;
                startTime = 0;
                result.date = new Date();
                result.mflops = Double.parseDouble(mflopsFormat.format(result.mflops));
                result.nres = Double.parseDouble(nResFormat.format(result.nres));

                db.addResult(result);

                mflopsTextView.setText(result.mflops + "");
                mflopsTextView.setTextColor(result.mflops < 200 ? Color.RED : Color.GREEN);
                nresTextView.setText(result.nres + "");
                if(result.nres > 5)
                {
                    nresTextView.setTextColor(Color.YELLOW);
                }
                else if(result.nres > 10)
                {
                    nresTextView.setTextColor(Color.RED);
                }
                else
                {
                    nresTextView.setTextColor(Color.GREEN);
                }
                timeTextView.setText(result.time / 1000 + "s");
                precisionTextView.setText(result.precision + "");
                populateList();
                start_single.setEnabled(true);
            }
        });
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// TODO Auto-generated method stub
		readPrefs();
	}
}
