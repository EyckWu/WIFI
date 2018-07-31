package com.eyckwu.wifi.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
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
        super(context);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater.from(context).inflate(R.layout.switch_bar, this);
        switch_text = (TextView)findViewById(R.id.switch_text);
        mLabel = getResources().getString(R.string.switch_off_text);
        updateText();

        restricted_icon = (ImageView)findViewById(R.id.restricted_icon);
        setOnClickListener(this);
        setVisibility(GONE);

        switch_widget = (Switch)findViewById(R.id.switch_widget);
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

    public void setCheck(boolean checked){
        setTextViewLabel(checked);
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
        switch_text.setText(mLabel);
    }

    @Override
    public void onClick(View v) {
        boolean check = !switch_widget.isChecked();
        setCheck(check);
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
