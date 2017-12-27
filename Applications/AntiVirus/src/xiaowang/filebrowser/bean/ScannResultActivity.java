package xiaowang.filebrowser.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import xiaowang.filebrowser.biz.FileHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ScannResultActivity extends Activity {

	private TextView virus_count;
	private ListView virus_list;
	ArrayList<String> virusName = new ArrayList<String>();
	ArrayList<String> virusPath = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scannresult);
		
		virus_count=(TextView) findViewById(R.id.virus_count);
		virus_list=(ListView) findViewById(R.id.virus_list);
		
		   Intent intent=getIntent();
		   Bundle bundle=intent.getExtras();
		   virusName=bundle.getStringArrayList("virusName");
		   virusPath=bundle.getStringArrayList("virusPath");
		   virus_count.setText("扫描结束，本次扫描发现了"+virusName.size()+"个病毒");
		
		SimpleAdapter ladapter = new SimpleAdapter(this,getMapData(virusName,virusPath),R.layout.virus_list_item, new String[]{"virus_name","virus_path"},new int[]{R.id.virus_name,R.id.virus_path});
		virus_list.setAdapter(ladapter);
		virus_list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, final int position,
					long id) {
//				Intent intent =new Intent();
//				intent.putExtra("filepath", virusPath.get(position).substring(virusPath.get(position).indexOf(":")+1,virusPath.get(position).lastIndexOf("/")));
//			    intent.setClass(ScannResultActivity.this, MainActivity.class);
//			    startActivity(intent);
		        final FileHelper fileHelper=new FileHelper(ScannResultActivity.this);
		        virusPath.get(position).substring(virusPath.get(position).indexOf(":")+1);
		        AlertDialog.Builder builder = new AlertDialog.Builder(ScannResultActivity.this);  
				 builder.setTitle("删除")
				        .setMessage("建议删除病毒文件,\n您确定要进行嘛？")  
				        .setPositiveButton("确定", new DialogInterface.OnClickListener() {  
				           public void onClick(DialogInterface dialog, int id) { 
				        	  boolean b= fileHelper.deleteSDFile(virusPath.get(position).substring(virusPath.get(position).indexOf(":")+1));
				        	  if (b) {
				        		  dialog.cancel();   
				        		  finish();
							}
				        	  else{
				        		  Toast.makeText(ScannResultActivity.this,"删除病毒失败", Toast.LENGTH_LONG).show();
				        	  }
				           }
				      }
				)
				        .setNegativeButton("取消", null)
				        .show();
		        
			}
		});
	}

	private ArrayList<Map<String, Object>> getMapData(
			ArrayList<String> virusName, ArrayList<String> virusPath) {
		ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		HashMap<String, Object> item;
		int i = 0;
		for (i = 0; i < virusName.size(); i++) {
			item = new HashMap<String, Object>();
			String virus_name = virusName.get(i).toString();
			String virus_path = virusPath.get(i).toString();

			item.put("virus_name", virus_name);
			item.put("virus_path", virus_path);
			data.add(item);
		}
		return data;
	}

}
