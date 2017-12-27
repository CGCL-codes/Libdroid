package xiaowang.filebrowser.bean;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class BadAppActivity extends Activity {
    private ListView mylv;
    private List<ApplicationInfo> badApp_infos = new ArrayList<ApplicationInfo>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_badapp);
        
        badApp_infos = FileBrowser.badApp_infos;
        
        mylv = (ListView) findViewById(R.id.listbody);
        mylv.setAdapter(new ListAppAdapter());
        mylv.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                uninstallAPK(badApp_infos.get(arg2).packageName);
            }
        });
    }
    /**
     * uninstall apk file
     * @param packageName 
     */
    public void uninstallAPK(String packageName){
        Uri uri=Uri.parse("package:"+packageName);
        Intent intent=new Intent(Intent.ACTION_DELETE,uri);
        startActivity(intent);
    }

    public class ListAppAdapter extends BaseAdapter {
        LayoutInflater inflater;

        public ListAppAdapter() {
            inflater = LayoutInflater.from(BadAppActivity.this);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {

                holder = new ViewHolder();
                convertView = inflater.inflate(R.layout.badapp_list_item, null);
                holder.icon = (ImageView) convertView
                        .findViewById(R.id.badlist_icon);
                holder.text_name = (TextView) convertView
                        .findViewById(R.id.badlist_name);
                convertView.setTag(holder);
            } else {

                holder = (ViewHolder) convertView.getTag();
            }

//            ResolveInfo info = mApps.get(position);
//            int permissionNum = 0;
//            for (int i = 0; i < Constants.permName.length; i++) {
//                int reslut = getPackageManager().checkPermission(
//                        Constants.permName[i], info.activityInfo.packageName);
//                if (reslut == PackageManager.PERMISSION_GRANTED) {
//                    permissionNum++;
//                }
//            }
            holder.icon.setImageResource(badApp_infos.get(position).icon);
            holder.text_name.setText(badApp_infos.get(position).packageName  );
            Log.e("test", "packageName--->"+badApp_infos.get(position).packageName);
            return convertView;
        }

        public final int getCount() {
            return badApp_infos.size();
        }

        public final Object getItem(int position) {
            return badApp_infos.get(position);
        }

        public final long getItemId(int position) {
            return position;
        }
    }

    class ViewHolder {
        ImageView icon;
        TextView text_name;
    }
}
