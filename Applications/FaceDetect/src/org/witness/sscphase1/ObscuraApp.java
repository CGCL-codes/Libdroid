package org.witness.sscphase1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.jason.lxcoff.lib.ControlMessages;
import org.jason.lxcoff.lib.ExecutionController;
import org.jason.lxcoff.lib.Configuration;
import org.witness.securesmartcam.ImageEditor;
import org.witness.ssc.video.VideoEditor;
import org.witness.sscphase1.Eula.OnEulaAgreedTo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
//import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

public class ObscuraApp extends Activity implements OnClickListener, OnEulaAgreedTo, OnSharedPreferenceChangeListener {
	    
	public final static String TAG = "SSC";
		
	final static int CAMERA_RESULT = 0;
	final static int GALLERY_RESULT = 1;
	final static int IMAGE_EDITOR = 2;
	final static int VIDEO_EDITOR = 3;
	final static int ABOUT = 0;
	
	final static String CAMERA_TMP_FILE = "ssctmp.jpg";
	
	private Button choosePictureButton, chooseVideoButton, takePictureButton;		
	
	private Uri uriCameraImage = null;
	
	private Configuration		config;
	private ExecutionController executionController;
	private Socket dirServiceSocket = null;
	InputStream is					= null;
	OutputStream os					= null;
	ObjectOutputStream oos			= null;
	ObjectInputStream ois			= null;
	private String imageFilePath = "/system/off-app/off-file/";
	private int picIndex			= 0;
	
	SharedPreferences settings;
	
	@Override
	protected void onDestroy() 
	{
		super.onDestroy();	
		deleteTmpFile();
		
	}
	
	private void deleteTmpFile ()
	{
		File fileDir = getExternalFilesDir(null);
		
		if (fileDir == null || !fileDir.exists())
			fileDir = getFilesDir();
		
		File tmpFile = new File(fileDir,CAMERA_TMP_FILE);
		if (tmpFile.exists())
			tmpFile.delete();
	}

	@SuppressLint("NewApi")				
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
     // I wanna use network in main thread, so ...
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        
        settings = PreferenceManager.getDefaultSharedPreferences(this); 
        settings.registerOnSharedPreferenceChangeListener(this);     
        
        createNotOffloadedFile();
        
		try {
			getInfoFromDirService();

		} catch (FileNotFoundException e) {
			Log.e(TAG, "Could not read the config file: " + ControlMessages.PHONE_CONFIG_FILE);
			return ;
		} /*catch (UnknownHostException e) {
			Log.e(TAG, "Could not connect: " + e.getMessage());
		} */catch (IOException e) {
			Log.e(TAG, "IOException: " + e.getMessage());
			//return ;
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "Could not find Clone class: " + e.getMessage());
			return;
		}
		// Create an execution controller
		this.executionController = new ExecutionController(
				this.dirServiceSocket,
				is, os, ois, oos,
				getPackageName(),
				getPackageManager(),
				this);

		readPrefs();
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setLayout();
        deleteTmpFile();
        
        Eula.show(this);
        

    }
    
    /**
	 * Create an empty file on the phone in order to let the method know
	 * where is being executed (on the phone or on the clone).
	 */
	private void createNotOffloadedFile(){
		try {
			File f = new File(ControlMessages.FILE_NOT_OFFLOADED);
			f.createNewFile();
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
	}
	
	/**
	 * Read the config file to get the IP and port for DirectoryService.
	 * @throws IOException 
	 * @throws UnknownHostException 
	 * @throws ClassNotFoundException 
	 */
	private void getInfoFromDirService() throws UnknownHostException, IOException, ClassNotFoundException {
		config = new Configuration(ControlMessages.PHONE_CONFIG_FILE);
		config.parseConfigFile(null, null);
		
		try{
			this.dirServiceSocket = new Socket();
			this.dirServiceSocket.connect(new InetSocketAddress(config.getDirServiceIp(), config.getDirServicePort()), 3000);
			this.os = this.dirServiceSocket.getOutputStream();
			this.is = this.dirServiceSocket.getInputStream();

			os.write(ControlMessages.PHONE_CONNECTION);

			oos = new ObjectOutputStream(os);
			ois = new ObjectInputStream(is);

			// Send the name and id to DirService
			os.write(ControlMessages.PHONE_AUTHENTICATION);
			oos.writeObject(ExecutionController.myId);
			oos.flush();
			
		} 
		finally {
			
		}
	}
    
    @Override
	protected void onResume() {

		super.onResume();
		
		 final SharedPreferences preferences = getSharedPreferences(Eula.PREFERENCES_EULA,
	                Activity.MODE_PRIVATE);
		  
	        if (preferences.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {
		
	        }
				
	
	}

	private void setLayout() {
		
        setContentView(R.layout.mainmenu);

		choosePictureButton = (Button) this.findViewById(R.id.ChoosePictureButton);
    	choosePictureButton.setOnClickListener(this);
    	
    	chooseVideoButton = (Button) this.findViewById(R.id.ChooseVideoButton);
    	chooseVideoButton.setOnClickListener(this);
    	
    	takePictureButton = (Button) this.findViewById(R.id.TakePictureButton);
    	takePictureButton.setOnClickListener(this);
		
    }

	public void onClick(View v) {
		if (v == choosePictureButton) 
		{
			OffDetect detect = new OffDetect(this.executionController);
			
			int target = picIndex % 20;
			int result = 0;
			long stime = System.nanoTime();
			for(int i=0; i<20; i++){
				result = detect.GetFace(this.imageFilePath + "face" + i%20 + ".jpg");
			}
			long dura = System.nanoTime() - stime;
			
			Log.i(TAG, "Photo "+ target +" is chosen. Detecting Face Nums: " + result + ". Cost " + dura/1000000 + "ms.");
			Toast.makeText(this, "Detecting Face Nums: " + result + ". Cost " + dura/1000000 + "ms.", Toast.LENGTH_SHORT).show();
			
			picIndex ++ ;
/*			
			try
			{
				setContentView(R.layout.mainloading);
				Intent intent = new Intent(Intent.ACTION_PICK);
				intent.setType("image/*"); //limit to image types for now
				startActivityForResult(intent, GALLERY_RESULT);
				
			}
			catch (Exception e)
			{
				Toast.makeText(this, "Unable to open Gallery app", Toast.LENGTH_LONG).show();
				Log.e(TAG, "error loading gallery app to choose photo: " + e.getMessage(), e);
			}*/
			
		} 
		else if (v == chooseVideoButton) 
		{
			
			try
			{
				 setContentView(R.layout.mainloading);
				Intent intent = new Intent(Intent.ACTION_PICK);
				intent.setType("video/*"); //limit to image types for now
				startActivityForResult(intent, GALLERY_RESULT);
				
			}
			catch (Exception e)
			{
				Toast.makeText(this, "Unable to open Gallery app", Toast.LENGTH_LONG).show();
				Log.e(TAG, "error loading gallery app to choose photo: " + e.getMessage(), e);
			}
			
		}
		else if (v == takePictureButton) {
			
			setContentView(R.layout.mainloading);
			
			String storageState = Environment.getExternalStorageState();
	        if(storageState.equals(Environment.MEDIA_MOUNTED)) {

	          
	            ContentValues values = new ContentValues();
	          
	            values.put(MediaStore.Images.Media.TITLE, CAMERA_TMP_FILE);
	      
	            values.put(MediaStore.Images.Media.DESCRIPTION,"ssctmp");

	            File tmpFileDirectory = new File(Environment.getExternalStorageDirectory().getPath() + ImageEditor.TMP_FILE_DIRECTORY);
	            if (!tmpFileDirectory.exists())
	            	tmpFileDirectory.mkdirs();
	            
	            File tmpFile = new File(tmpFileDirectory,"cam" + ImageEditor.TMP_FILE_NAME);
	        	
	        	uriCameraImage = Uri.fromFile(tmpFile);
	            //uriCameraImage = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

	            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE );
	            intent.putExtra( MediaStore.EXTRA_OUTPUT, uriCameraImage);
	            
	            startActivityForResult(intent, CAMERA_RESULT);
	        }   else {
	            new AlertDialog.Builder(ObscuraApp.this)
	            .setMessage("External Storeage (SD Card) is required.\n\nCurrent state: " + storageState)
	            .setCancelable(true).create().show();
	        }
	        
			takePictureButton.setVisibility(View.VISIBLE);
			choosePictureButton.setVisibility(View.VISIBLE);
			chooseVideoButton.setVisibility(View.VISIBLE);
			
		} 
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		
		
		if (resultCode == RESULT_OK)
		{
			setContentView(R.layout.mainloading);
			
			if (requestCode == GALLERY_RESULT) 
			{
				if (intent != null)
				{
					Uri uriGalleryFile = intent.getData();
					
					try
						{
							if (uriGalleryFile != null)
							{
								Cursor cursor = managedQuery(uriGalleryFile, null, 
		                                null, null, null); 
								cursor.moveToNext(); 
								// Retrieve the path and the mime type 
								String path = cursor.getString(cursor 
								                .getColumnIndex(MediaStore.MediaColumns.DATA)); 
								String mimeType = cursor.getString(cursor 
								                .getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
								
								if (mimeType == null || mimeType.startsWith("image"))
								{
									Intent passingIntent = new Intent(this,ImageEditor.class);
									passingIntent.setData(uriGalleryFile);
									startActivityForResult(passingIntent,IMAGE_EDITOR);
								}
								else if (mimeType.startsWith("video"))
								{
		
									Intent passingIntent = new Intent(this,VideoEditor.class);
									passingIntent.setData(uriGalleryFile);
									startActivityForResult(passingIntent,VIDEO_EDITOR);
								}
							}
							else
							{
								Toast.makeText(this, "Unable to load media.", Toast.LENGTH_LONG).show();
			
							}
						}
					catch (Exception e)
					{
						Toast.makeText(this, "Unable to load media.", Toast.LENGTH_LONG).show();
						Log.e(TAG, "error loading media: " + e.getMessage(), e);

					}
				}
				else
				{
					Toast.makeText(this, "Unable to load photo.", Toast.LENGTH_LONG).show();
	
				}
					
			}
			else if (requestCode == CAMERA_RESULT)
			{
				//Uri uriCameraImage = intent.getData();
				
				if (uriCameraImage != null)
				{
					Intent passingIntent = new Intent(this,ImageEditor.class);
					passingIntent.setData(uriCameraImage);
					startActivityForResult(passingIntent,IMAGE_EDITOR);
				}
				else
				{
					takePictureButton.setVisibility(View.VISIBLE);
					choosePictureButton.setVisibility(View.VISIBLE);
				}
			}
		}
		else
			setLayout();
		
		
		
	}	

	/*
	 * Display the about screen
	 */
	private void displayAbout() {
		
		StringBuffer msg = new StringBuffer();
		
		msg.append(getString(R.string.app_name));
		
        String versNum = "";
        
        try {
            String pkg = getPackageName();
            versNum = getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
        	versNum = "";
        }
        
        msg.append(" v" + versNum);
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about));
	        
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about2));
        
        msg.append('\n');
        msg.append('\n');
        
        msg.append(getString(R.string.about3));
        
		showDialog(msg.toString());
	}
	
	private void showDialog (String msg)
	{
		 new AlertDialog.Builder(this)
         .setTitle(getString(R.string.app_name))
         .setMessage(msg)
         .create().show();
	}


	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		
		String aboutString = "About ObscuraCam";
		
    	MenuItem aboutMenuItem = menu.add(Menu.NONE, ABOUT, Menu.NONE, aboutString);
    	aboutMenuItem.setIcon(R.drawable.ic_menu_about);
    	menu.add(Menu.NONE, 99, Menu.NONE, "Location Settings");
    	
    	return true;
	}
	
    public boolean onOptionsItemSelected(MenuItem item) {	
        switch (item.getItemId()) {
        	case ABOUT:
        		displayAbout();
        		return true;
        	case 99:
        		Intent intent = new Intent(getApplicationContext(), Preferences.class);
        		startActivity(intent);
        		return true;
        	default:
        		
        		return false;
        }
    }
    
	
    /*
     * Handling screen configuration changes ourselves, 
     * we don't want the activity to restart on rotation
     */
/*    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
        // Reset the layout to use the landscape config
        setLayout();
    }*/

	@Override
	public void onEulaAgreedTo() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,String key) {
		// TODO Auto-generated method stub
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
    
}
