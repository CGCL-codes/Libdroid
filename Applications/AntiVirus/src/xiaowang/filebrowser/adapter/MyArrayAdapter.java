package xiaowang.filebrowser.adapter;

import java.util.List;

import xiaowang.filebrowser.bean.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MyArrayAdapter extends ArrayAdapter<String> {

	private int resouceId;
	private LayoutInflater inflater;
	public MyArrayAdapter(Context context, int textViewResourceId,
			List<String> objects) {
		super(context, textViewResourceId, objects);
		this.resouceId=textViewResourceId;
		this.inflater=LayoutInflater.from(context);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LinearLayout layout;
		 if(convertView==null){
			 layout=(LinearLayout) inflater.inflate(resouceId, null);
		 }else{
			 layout=(LinearLayout) convertView;
		 }
		 TextView tv=(TextView) layout.findViewById(R.id.catalog_item);
		 tv.setText(getItem(position));
		return layout;
	}
}
