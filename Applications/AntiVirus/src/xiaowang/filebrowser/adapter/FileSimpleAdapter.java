package xiaowang.filebrowser.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FileSimpleAdapter extends SimpleAdapter {
	private boolean load=true;
	public boolean isLoad() {
		return load;
	}
	public void setLoad(boolean load) {
		this.load = load;
	}
	private List<Integer> selecteds=new ArrayList<Integer>();
	public void setSelecteds(List<Integer> selecteds) {
		this.selecteds = selecteds;
	}
	public List<Integer> getSelecteds() {
		return selecteds;
	}
	private HashMap<Integer,Boolean> isSelected;
	
	private int[] mTo;
    private String[] mFrom;
    private ViewBinder mViewBinder;
    
    private List<? extends Map<String, ?>> mData;
    private int mResource;
    @SuppressWarnings("unused")
	private int mDropDownResource;
    private LayoutInflater mInflater;

	public FileSimpleAdapter(Context context,
			List<? extends Map<String, ?>> data, int resource, String[] from,
			int[] to) {
		super(context, data, resource, from, to);
		
		mData = data;
        mResource = mDropDownResource = resource;
        mFrom = from;
        mTo = to;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        isSelected = new HashMap<Integer,Boolean>();
        for(int i = 0; i<data.size(); i++){
			isSelected.put(i, false);
		}

	}
	
	
	 /**
     * @see android.widget.Adapter#getView(int, View, ViewGroup)
     */
    public View getView(int position, View convertView, ViewGroup parent) {

        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView,
            ViewGroup parent, int resource) {
        View v;
        if (convertView == null) {
            v = mInflater.inflate(resource, parent, false);
            
            final int[] to = mTo;
            final int count = to.length;
            final View[] holder = new View[count];

            for (int i = 0; i < count; i++) {
                holder[i] = v.findViewById(to[i]);
            }

            v.setTag(holder);
        } else {
            v = convertView;
        }
        if(load){
        	bindView(position, v);
        }
        return v;
    }
    
    private void bindView(final int position, View view) {
        final Map<?, ?> dataSet = mData.get(position);
        if (dataSet == null) {
            return;
        }

        final ViewBinder binder = mViewBinder;
        final View[] holder = (View[]) view.getTag();
        final String[] from = mFrom;
        final int[] to = mTo;
        final int count = to.length;

        for (int i = 0; i < count; i++) {
            final View v = holder[i];
            if (v != null) {
                final Object data = dataSet.get(from[i]);
                String text = data == null ? "" : data.toString();
                if (text == null) {
                    text = "";
                }

                boolean bound = false;
                if (binder != null) {
                    bound = binder.setViewValue(v, data, text);
                }

                if (!bound) {
                    if (v instanceof Checkable) {
                        if (data instanceof Boolean) {
                            ((Checkable) v).setChecked(isSelected.get(position));
                            v.setOnClickListener(new OnClickListener() {
								
								public void onClick(View v) {
									if(isSelected.get(position)){
										isSelected.put(position, false);
										selecteds.remove((Integer)position);
									}else{
										isSelected.put(position, true);
										selecteds.add(position);
									}
								}
							});
                        } else {
                            throw new IllegalStateException(v.getClass().getName() +
                                    " should be bound to a Boolean, not a " + data.getClass());
                        }
                    }else if(v instanceof ProgressBar){
                    	if(data instanceof Integer){
                    		((ProgressBar) v).setProgress((Integer) data);
                    	}else{
                    		throw new IllegalStateException(v.getClass().getName() +
                                    " should be bound to a Integer, not a " + data.getClass());
                    	}
                    }else if (v instanceof TextView) {
                        // Note: keep the instanceof TextView check at the bottom of these
                        // ifs since a lot of views are TextViews (e.g. CheckBoxes).
                    	
                    	setViewText((TextView) v, text);
                    } else if (v instanceof ImageView) {
                    	
                    	
//                    	Bitmap bitmap = WebImageBuilder.returnBitMap("http://timg3.ddmapimg.com/city/images/citynew/2696c2126e903cf8d-7f23.jpg");
//                    	((ImageView) v).setImageBitmap(bitmap);
//                    	setViewImage((ImageView) v,"http://timg3.ddmapimg.com/city/images/citynew/2696c2126e903cf8d-7f23.jpg");
                        if (data instanceof Integer) {
                            setViewImage((ImageView) v, (Integer) data);                            
                        }else if(data instanceof Drawable){
                        	setViewImage((ImageView) v, (Drawable) data);
                        } 
                        else if(data instanceof String){
                            setViewImage((ImageView) v, (String) data);
                        }
                    } else {
                        throw new IllegalStateException(v.getClass().getName() + " is not a " +
                                " view that can be bounds by this SimpleAdapter");
                    }
                }
            }
        }
    }
 
    public void setViewImage(ImageView v, int value) {
        v.setImageResource(value);
    }

    public void setViewImage(ImageView v, String value) {
//    	Bitmap bitmap = WebImageBuilder.returnBitMap(value);
//    	((ImageView) v).setImageBitmap(bitmap);
//    	Bitmap很占内存，极其容易引起OutOfMemoryError,需要使用BitmapFactory.Options的对象对Bitmap进行修改
    	
    	BitmapFactory.Options options = new BitmapFactory.Options();
    	options.inJustDecodeBounds=true;
    	Bitmap bmp=BitmapFactory.decodeFile(value, options);
    	int srcHeight=options.outHeight;
    	int srcWidth=options.outWidth;
    	int scale=srcHeight/48+1;
    	
    	BitmapFactory.Options newOptions = new BitmapFactory.Options();
    	newOptions.inSampleSize=scale;
    	newOptions.inJustDecodeBounds=false;
    	newOptions.outHeight=srcHeight/scale;
    	newOptions.outWidth=srcWidth/scale;
    	bmp=BitmapFactory.decodeFile(value, newOptions);
    	
    	((ImageView)v).setImageBitmap(bmp);
    	if(bmp!=null){
    		bmp.isRecycled();
    		bmp=null;
    	}
    	System.gc();
    }

    public void setViewImage(ImageView v, Drawable value){
    	((ImageView)v).setImageDrawable(value);
    	value=null;
    }
    public void setViewText(TextView v, String text) {
    	
    	super.setViewText(v, text);    	
    }
    public void noneIsSelected(){
    	for(int i = 0; i<mData.size(); i++){
			isSelected.put(i, false);
		}
    	selecteds.removeAll(selecteds);
    	notifyDataSetChanged();
    }
}    