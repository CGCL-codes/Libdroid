package xiaowang.filebrowser.bean;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class PermissionActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text=new TextView(this);
		Intent intent=getIntent();
		String textContent="";
		ArrayList<String> list=intent.getStringArrayListExtra("permissions");
		for (String string : list) {
			textContent+=string+"\n";
		}
		text.setText(textContent);
		setContentView(text);
	}
	
	

}
