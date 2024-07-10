package sound.recorder.widget.util;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;

/**
 * extension of RecyclerView.Adapter to management data.
 *
 * Created by will on 15/9/2.
 */
public abstract class BaseRecyclerViewAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<T> mList;
    protected Context mContext;


    BaseRecyclerViewAdapter(Context context) {
        this.mContext = context;
    }

    @Override
    public int getItemCount() {
        if (mList != null)
            return mList.size();
        else
            return 0;
    }

    public void setData(ArrayList<T> data) {
        this.mList = data;
        notifyDataSetChanged();
    }

    public void addItem(ArrayList<T> list) {
        mList.addAll(list);
        notifyDataSetChanged();
    }

    public ArrayList<T> getData() {
        return mList;
    }

    public void setData(T[] list) {
        ArrayList<T> arrayList = new ArrayList<>(list.length);
        Collections.addAll(arrayList, list);
        setData(arrayList);
    }


    public void addData(int position, T item) {
        if (mList != null && position < mList.size()) {
            mList.add(position, item);
            notifyMyItemInserted(position);
        }
    }

    public void removeData(int position) {
        if (mList != null && position < mList.size()) {
            mList.remove(position);
            notifyMyItemRemoved(position);
        }
    }

    protected void notifyMyItemInserted(int position){
        notifyItemInserted(position);
    }

    protected void notifyMyItemRemoved(int position){
        notifyItemRemoved(position);
    }

    protected void notifyMyItemChanged(int position){
        notifyItemChanged(position);
    }

    public interface OnItemClickLitener {
        void onItemClick(View view, int position);
    }


}
