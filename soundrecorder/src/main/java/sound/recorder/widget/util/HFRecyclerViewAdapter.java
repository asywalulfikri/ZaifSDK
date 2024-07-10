package sound.recorder.widget.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class HFRecyclerViewAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseRecyclerViewAdapter<T> {

    public HFRecyclerViewAdapter(Context context) {
        super(context);
    }

    private static final int TYPE_HEADER = Integer.MAX_VALUE;
    private static final int TYPE_FOOTER = Integer.MAX_VALUE - 1;
    private static final int ITEM_MAX_TYPE = Integer.MAX_VALUE - 2;
    private RecyclerView.ViewHolder headerViewHolder;
    private RecyclerView.ViewHolder footerViewHolder;

    class HFViewHolder extends RecyclerView.ViewHolder {
        HFViewHolder(View v) {
            super(v);
        }
    }

    public void setFooterView(View foot){
        if (footerViewHolder == null || foot != footerViewHolder.itemView) {
            footerViewHolder = new HFViewHolder(foot);
            notifyDataSetChanged();
        }
    }

    public void removeFooter(){
        if (footerViewHolder != null){
            footerViewHolder = null;
            notifyDataSetChanged();
        }
    }

    private boolean isHeader(int position){
        return hasHeader() && position == 0;
    }

    private boolean isFooter(int position){
        return hasFooter() && position == getDataItemCount() + (hasHeader() ? 1 : 0);
    }

    private int itemPositionInData(int rvPosition){
        return rvPosition - (hasHeader() ? 1 : 0);
    }
    private int itemPositionInRV(int dataPosition){
        return dataPosition + (hasHeader() ? 1 : 0);
    }

    @Override
    public void notifyMyItemInserted(int itemPosition) {
        notifyItemInserted(itemPositionInRV(itemPosition));
    }
    @Override
    public void notifyMyItemRemoved(int itemPosition) {
        notifyItemRemoved(itemPositionInRV(itemPosition));
    }

    @Override
    public void notifyMyItemChanged(int itemPosition) {
        notifyItemChanged(itemPositionInRV(itemPosition));
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return headerViewHolder;
        } else if (viewType == TYPE_FOOTER) {
            return footerViewHolder;
        }
        return onCreateDataItemViewHolder(parent, viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (!isHeader(position) && !isFooter(position))
            onBindDataItemViewHolder((VH)holder, itemPositionInData(position));

        if (isFooter(position)){
            footerOnVisibleItem();
        }
    }

    public abstract void footerOnVisibleItem();

    @Override
    public int getItemCount() {
        int itemCount = getDataItemCount();
        if (hasHeader()) {
            itemCount += 1;
        }
        if (hasFooter()) {
            itemCount += 1;
        }
        return itemCount;
    }

    @Override
    public int getItemViewType(int position) {
        if (isHeader(position)) {
            return TYPE_HEADER;
        }
        if (isFooter(position)) {
            return TYPE_FOOTER;
        }
        int dataItemType = getDataItemType(itemPositionInData(position));
        if (dataItemType > ITEM_MAX_TYPE) {
            throw new IllegalStateException("getDataItemType() must be less than " + ITEM_MAX_TYPE + ".");
        }
        return dataItemType;
    }

    private int getDataItemCount() {
        return super.getItemCount();
    }

    /**
     * make sure your dataItemType < Integer.MAX_VALUE-1
     *
     * @param position item view position in rv
     * @return item viewType
     */
    private int getDataItemType(int position){
        return 0;
    }


    private boolean hasHeader(){
        return headerViewHolder != null;
    }
    private boolean hasFooter(){
        return footerViewHolder != null;
    }

    public abstract VH onCreateDataItemViewHolder(ViewGroup parent, int viewType);

    public abstract void onBindDataItemViewHolder(VH holder, int position);



}
