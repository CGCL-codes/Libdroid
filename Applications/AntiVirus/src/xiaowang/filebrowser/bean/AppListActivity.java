package xiaowang.filebrowser.bean;

import java.util.ArrayList;
import java.util.List;

import xiaowang.filebrowser.contants.Constants;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



public class AppListActivity extends Activity implements OnItemClickListener{


		private ListView mylv;
		private List<ResolveInfo> mApps;
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applist);
        
        mylv=(ListView)findViewById(R.id.listbody);
        
        new AsyncTask<Integer, Integer, String[]>(){

			private ProgressDialog dialog;
			//前台显示
			protected void onPreExecute(){
				dialog = ProgressDialog.show(AppListActivity.this, "",
						"正在扫描,请稍候....");
				super.onPreExecute();
			}
			//后台执行
			protected String[] doInBackground(Integer... params){
			       
				loadApps();
				
				return null;
			}
			//执行完毕
			protected void onPostExecute(String[] result){
				    dialog.dismiss();
				    Toast.makeText(AppListActivity.this, "程序扫描完毕", Toast.LENGTH_LONG).show();
			        mylv.setAdapter(new ListAppAdapter());

				super.onPostExecute(result);
			}
		}.execute();
		mylv.setOnItemClickListener(this);
    }
private void loadApps() {
   // TODO Auto-generated method stub
   Intent mainintent=new Intent(Intent.ACTION_MAIN,null);
   mainintent.addCategory(Intent.CATEGORY_LAUNCHER);
   mApps=this.getPackageManager().queryIntentActivities(mainintent, 0);
}
public class ListAppAdapter extends BaseAdapter {
	   LayoutInflater inflater;
	public ListAppAdapter() {
	   inflater=LayoutInflater.from(AppListActivity.this);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
	ViewHolder holder;

	if (convertView == null) {

	   holder=new ViewHolder();
	   convertView = inflater.inflate(R.layout.list_item, null);
	   holder.icon=(ImageView)convertView.findViewById(R.id.list_icon);
	   holder.text_name=(TextView)convertView.findViewById(R.id.list_name);
	   holder.text_size=(TextView)convertView.findViewById(R.id.list_size);
	   convertView.setTag(holder);
	} else {

	holder = (ViewHolder) convertView.getTag();
	}

	ResolveInfo info = mApps.get(position);
	int permissionNum=0;
	 for (int i = 0; i < Constants.permName.length; i++) {
    	 int reslut = getPackageManager().checkPermission(Constants.permName[i], info.activityInfo.packageName);
    	 if(reslut == PackageManager.PERMISSION_GRANTED){
    		 permissionNum++;
         }
	} 
	holder.icon.setImageDrawable(info.activityInfo.loadIcon(getPackageManager()));
	holder.text_name.setText(info.activityInfo.loadLabel(getPackageManager()));
	holder.text_size.setText(permissionNum+"条权限");
	return convertView;
	}


	public final int getCount() {
	return mApps.size();
	}

	public final Object getItem(int position) {
	return mApps.get(position);
	}

	public final long getItemId(int position) {
	return position;
	}
	}
	class ViewHolder{
	   ImageView icon;
	   TextView text_name;
	   TextView text_size;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	   ArrayList<String> permissions=new ArrayList<String>();
	   ResolveInfo info = mApps.get(position);
	   
	   for (int i = 0; i < Constants.permName.length; i++) {
	    	 int reslut = getPackageManager().checkPermission(Constants.permName[i], info.activityInfo.packageName);
	    	 if(reslut == PackageManager.PERMISSION_GRANTED){
	    		 permissions.add(Constants.permName[i]);
	         }
		} 
	   if (permissions.size()==0) {
		Toast.makeText(this, "此程序没有申请什么权限", Toast.LENGTH_SHORT).show();
	}else{
	   Intent intent = new Intent();
	   intent.putStringArrayListExtra("permissions", permissions);
	   intent.setClass(this,PermissionActivity.class);//info.activityInfo.name
	   startActivity(intent);
	   }
	}
	  
	}

