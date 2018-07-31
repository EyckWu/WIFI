package com.eyckwu.wifi.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Switch;

/**
 * Created by EyckWu on 2018/7/30.
 *
 */

public class ToggleSwitch extends Switch {

    private ToggleSwitch.OnBeforeCheckedChangeListener mListener;

    public interface OnBeforeCheckedChangeListener{
        public boolean onBeforeChecked(ToggleSwitch toggleSwitch, boolean check);
    }
    public ToggleSwitch(Context context) {
        super(context);
    }

    public ToggleSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ToggleSwitch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOnBeforeCheckedChangeListener(OnBeforeCheckedChangeListener l){
        this.mListener = l;
    }

    @Override
    public void setChecked(boolean checked) {
        if(mListener != null && mListener.onBeforeChecked(this, checked)) {
            return;
        }
        super.setChecked(checked);
    }

    public void setCheckInternal(boolean checked){
        super.setChecked(checked);
    }
}
