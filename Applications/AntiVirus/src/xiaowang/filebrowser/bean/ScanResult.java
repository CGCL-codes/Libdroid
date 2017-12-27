package xiaowang.filebrowser.bean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import xiaowang.filebrowser.adapter.MyArrayAdapter;
import xiaowang.filebrowser.biz.MIMEType;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ScanResult extends ListActivity {

	private ArrayList<String> listStr;
	
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		//requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.listview2);
		setTitle("搜索结果：");
		ArrayAdapter<String> adapter = new MyArrayAdapter(this,
				R.layout.catalog_row, FileToStr(FileBrowser.resultList));
		setListAdapter(adapter);
	}

/**
* 把文件列表转换成字符串
* 
* @param f
* @return
*/
	public List<String> FileToStr(List<File> f){
		listStr = new ArrayList<String>();
		listStr.clear();
		int len=f.size();
		for (int i = 0; i < len; i++)
		{
			String nameString = f.get(i).getAbsolutePath();
			listStr.add(nameString);
		}
		return listStr;
	}
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
	
	//position为点击所在的位置 用这个序号来取得对应文件
		File file = FileBrowser.resultList.get(position);

		MIMEType.openFile(file, this);

	}
}