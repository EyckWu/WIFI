package com.eyckwu.wifi.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.eyckwu.wifi.R;

import java.util.ArrayList;

/**
 * Created by EyckWu on 2018/7/30.
 */

public class SwitchBar extends LinearLayout implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = SwitchBar.class.getSimpleName();
    public interface OnSwitchChangeListener{
        void onSwitchChanged(Switch switchView, boolean isChecked);
    }

    private TextView switch_text;
    private ImageView restricted_icon;
    private Switch switch_widget;
    private String mLabel;
    private String mSummary;

    private static int[] XML_ATTRIBUTES = {
            R.attr.switchBarMarginStart, R.attr.switchBarMarginEnd,
            R.attr.switchBarBackgroundColor};
    private ArrayList<OnSwitchChangeListener> mSwitchChangeListeners =
            new ArrayList<OnSwitchChangeListener>();

    public SwitchBar(Context context) {
        this(context, null);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, -1);
    }

    public SwitchBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        View view = LayoutInflater.from(context).inflate(R.layout.switch_bar, this);
        switch_text = (TextView)view.findViewById(R.id.switch_text);
        mLabel = getResources().getString(R.string.switch_off_text);
        updateText();

        restricted_icon = (ImageView)view.findViewById(R.id.restricted_icon);
        setOnClickListener(this);
        setVisibility(GONE);

        switch_widget = (Switch)view.findViewById(R.id.switch_widget);
        addOnSwitchChangeListener(new OnSwitchChangeListener() {
            @Override
            public void onSwitchChanged(Switch switchView, boolean isChecked) {
                setTextViewLabel(isChecked);
            }
        });

    }

    private void setTextViewLabel(boolean isChecked) {
        mLabel = getResources().getString(isChecked ? R.string.switch_on_text : R.string.switch_off_text);
        updateText();
    }

    public void setSummary(String summary) {
        mSummary = summary;
        updateText();
    }

    public void setChecked(boolean checked){
        setTextViewLabel(checked);
        if(switch_widget ==null) {
            Log.w(TAG, "switch_widget ==null");
        }
        switch_widget.setChecked(checked);
    }

    public boolean isChecked(){
        return switch_widget.isChecked();
    }

    public void setEnable(boolean enable){
        switch_text.setEnabled(enable);
        switch_widget.setEnabled(enable);
    }

    public void show(){
        if(!isShowing()) {
            setVisibility(VISIBLE);
            switch_widget.setOnCheckedChangeListener(this);
        }
    }

    public void hide(){
        if(isShowing()) {
            setVisibility(GONE);
            switch_widget.setOnCheckedChangeListener(null);
        }
    }

    public boolean isShowing(){
        return getVisibility() == View.VISIBLE;
    }

    public void addOnSwitchChangeListener(OnSwitchChangeListener listener) {
        if (mSwitchChangeListeners.contains(listener)) {
            throw new IllegalStateException("Cannot add twice the same OnSwitchChangeListener");
        }
        mSwitchChangeListeners.add(listener);
    }
    
    public void removeOnSwitchChangeListener(OnSwitchChangeListener listener){
        if(!mSwitchChangeListeners.contains(listener)) {
            throw new IllegalStateException("Cannot remove OnSwitchChangeListener");
        }
        mSwitchChangeListeners.remove(listener);
    }

    private void updateText() {
        if(switch_text == null) {
            Log.w(TAG, "switch_text == null");
        }else {
            Log.w(TAG, "mLabel == null");
            switch_text.setText(mLabel == null ? "WLAN" : mLabel );
        }

    }

    @Override
    public void onClick(View v) {
        boolean check = !switch_widget.isChecked();
        setChecked(check);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        propagateChecked(isChecked);
    }

    public void propagateChecked(boolean isChecked) {
        final int count = mSwitchChangeListeners.size();
        for (int n = 0; n < count; n++) {
            mSwitchChangeListeners.get(n).onSwitchChanged(switch_widget, isChecked);
        }
    }
}
