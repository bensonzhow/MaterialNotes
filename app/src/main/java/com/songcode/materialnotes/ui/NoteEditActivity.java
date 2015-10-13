/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.songcode.materialnotes.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.github.clans.fab.Label;
import com.songcode.materialnotes.R;
import com.songcode.materialnotes.data.Notes;
import com.songcode.materialnotes.data.Notes.TextNote;
import com.songcode.materialnotes.model.WorkingNote;
import com.songcode.materialnotes.model.WorkingNote.NoteSettingChangedListener;
import com.songcode.materialnotes.tool.BitmapUtil;
import com.songcode.materialnotes.tool.DataUtils;
import com.songcode.materialnotes.tool.ResourceParser;
import com.songcode.materialnotes.tool.ResourceParser.TextAppearanceResources;
import com.songcode.materialnotes.tool.TransitionHelper;
import com.songcode.materialnotes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import com.songcode.materialnotes.ui.NoteEditText.OnTextViewChangeListener;
import com.songcode.materialnotes.widget.NoteWidgetProvider_2x;
import com.songcode.materialnotes.widget.NoteWidgetProvider_4x;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NoteEditActivity extends TransitionHelper.BaseActivity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {

    private class HeadViewHolder {
        public ImageView ibSetBgColor;
    }

    private static final Map<Integer, Integer> colorBtnMap = new HashMap<Integer, Integer>();

    static {
        colorBtnMap.put(R.string.theme_blue, ResourceParser.BLUE);
        colorBtnMap.put(R.string.theme_red, ResourceParser.RED);
        colorBtnMap.put(R.string.theme_green, ResourceParser.GREEN);
        colorBtnMap.put(R.string.theme_brown, ResourceParser.BROWN);
        colorBtnMap.put(R.string.theme_white, ResourceParser.WHITE);
    }

    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();

    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();

    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;

    private View mAnimBackGroudView;

    private View mAnimNewNoteView;

    private View mAnimTargetView;

    private View mBackGroudView;

    private FloatingActionMenu mMenu;

    private Toolbar mToolbar;

    private View mFontSizeSelector;

    private EditText mNoteEditor;

    private View mNoteEditorPanel;

    private WorkingNote mWorkingNote;

    private SharedPreferences mSharedPrefs;

    private int mFontSizeId;

    private boolean isShowTheme;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    private LinearLayout mEditTextList;

    private String mUserQuery;
    private Pattern mPattern;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        setNoteTheme();

        String dateStr = DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_SHOW_YEAR);
        mToolbar.setTitle(dateStr);

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        mToolbar.setSubtitle(getSubTitle());
    }

    private void setNoteTheme() {
        int primaryColorId = mWorkingNote.getTitleBgResId();
        mNoteEditorPanel.setBackgroundResource(R.color.primary_color_white_light);
        setTheme(mWorkingNote.getToolbarThemeStyle());
        int primaryColor = getResources().getColor(primaryColorId);
        mToolbar.setBackgroundColor(primaryColor);
        mBackGroudView.setBackgroundColor(primaryColor);
        mMenu.setMenuButtonColorNormalResId(primaryColorId);
        mMenu.setMenuButtonColorPressedResId(mWorkingNote.getBgColorResId());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    private void initResources() {
        mBackGroudView = findViewById(R.id.note_bg);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditor.setAlpha(0);
        //floating action menu
        mMenu = (FloatingActionMenu) findViewById(R.id.note_edit_floating_action_menu);
        mMenu.setClosedOnTouchOutside(true);
        mMenu.setOnMenuToggleListener(onMenuToggleListener);
        mMenu.setOnMenuButtonClickListener(onClickFabMenu);

        //toolbar
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mAnimBackGroudView = findViewById(R.id.anim_back_groud_layout);
        //for anim backgroud
        if (getIntent().hasExtra("bitmap_id")) {
            mAnimBackGroudView.setBackground(new BitmapDrawable(getResources(), BitmapUtil.fetchBitmapFromIntent(getIntent())));
        }

        mAnimNewNoteView = findViewById(R.id.anim_new_note_view);
        mAnimTargetView = findViewById(R.id.anim_new_note_layout);

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }
        ;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnimNewNoteView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    mAnimNewNoteView.removeOnLayoutChangeListener(this);
                    animateRevealShow(mAnimTargetView, mAnimNewNoteView);
                }
            });
        }
    }

    private boolean isActionInsertOrEdit() {
        return TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, getIntent().getAction());
    }

    private boolean isActionActionView() {
        return TextUtils.equals(Intent.ACTION_VIEW, getIntent().getAction());
    }

    private void animateRevealShow(View targetview, View startView) {
        if (isActionInsertOrEdit()) {
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            int cx = startView.getLeft() + (startView.getWidth() / 2); //middle of button
            int cy = startView.getTop() + (startView.getHeight() / 2); //middle of button
            int radius = (int) Math.sqrt(Math.pow(cx, 2) + Math.pow(cy, 2)); //hypotenuse to top left

            Animator anim = ViewAnimationUtils.createCircularReveal(targetview, cx, cy, 0, radius);
            targetview.setVisibility(View.VISIBLE);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mNoteEditor.requestFocus();
                    imm.showSoftInput(mNoteEditor, 0);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            anim.setDuration(ANIM_DURATION);
            anim.start();
        } else if (isActionActionView()) {
            mAnimBackGroudView.animate().scaleX(.92f).scaleY(.92f).alpha(.6f).setDuration(ANIM_DURATION).setInterpolator(new AccelerateInterpolator()).start();
            mNoteEditor.animate().alpha(1).setStartDelay(400).setDuration(ANIM_DURATION);
            ViewCompat.setTransitionName(mAnimTargetView, "target_anim_view");
        }
    }

    private void animateRevealHide(final View targetview, View startView) {
        if (isActionInsertOrEdit()) {
            int cx = startView.getLeft() + (startView.getWidth() / 2); //middle of button
            int cy = startView.getTop() + (startView.getHeight() / 2); //middle of button
            int radius = (int) Math.sqrt(Math.pow(cx, 2) + Math.pow(cy, 2)); //hypotenuse to top left

            Animator anim = ViewAnimationUtils.createCircularReveal(targetview, cx, cy, radius, 0);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    targetview.setVisibility(View.INVISIBLE);
                }
            });
            //anim.setInterpolator(new AccelerateInterpolator());
            anim.setDuration(ANIM_DURATION);
            anim.start();

            Integer colorTo = getResources().getColor(R.color.primaryColor);
            Integer colorFrom = getResources().getColor(android.R.color.white);
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    targetview.setBackgroundColor((Integer) animator.getAnimatedValue());
                }

            });
            colorAnimation.setInterpolator(new AccelerateInterpolator(2));
            colorAnimation.setDuration(ANIM_DURATION);
            colorAnimation.start();
        } else if (isActionActionView()) {
            mAnimBackGroudView.animate().scaleX(1).scaleY(1).alpha(1).translationY(0).setDuration(ANIM_DURATION).setInterpolator(new AccelerateInterpolator()).start();
            mNoteEditor.animate().alpha(0).setDuration(100).start();
        }
    }

    private FloatingActionMenu.OnMenuToggleListener onMenuToggleListener = new FloatingActionMenu.OnMenuToggleListener() {
        @Override
        public void onMenuToggle(boolean opened) {
            if (isShowTheme && !opened) {
                isShowTheme = false;
                showThemeMenu();
            }
        }
    };

    private OnClickListener onClickFabMenu = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isFinishing()) {
                return;
            }
            if (mMenu.isOpened()) {
                mMenu.close(true);
            } else {
                prepareFabMenu();
            }
        }
    };

    private void prepareFabMenu() {
        clearSettingState();

        //prepare menu items
        //...menu string ids
        ArrayList<Integer> ids = new ArrayList<>();
        if (mWorkingNote.getFolderId() != Notes.ID_CALL_RECORD_FOLDER) {
            ids.add(R.string.notelist_menu_new);
        }
        ids.add(R.string.menu_delete);
        ids.add(R.string.menu_font_size);
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            ids.add(R.string.menu_normal_mode);
        } else {
            ids.add(R.string.menu_list_mode);
        }
        ids.add(R.string.menu_share);
        ids.add(R.string.menu_send_to_desktop);
        if (mWorkingNote.hasClockAlert()) {
            ids.add(R.string.menu_remove_remind);
        } else {
            ids.add(R.string.menu_alert);
        }
        ids.add(R.string.notelist_menu_color_theme);
        addMenuItemAndOpenMenu(ids);
    }

    private void addMenuItemAndOpenMenu(List<Integer> stringIds) {
        if (mMenu == null) {
            Log.e(TAG, "error: floating button menu is null");
            return;
        }
        mMenu.removeAllMenuButtons();
        for (int stringId : stringIds) {
            final FloatingActionButton floatingActionButton = new FloatingActionButton(this);
            floatingActionButton.setButtonSize(FloatingActionButton.SIZE_MINI);
            floatingActionButton.setColorNormalResId(mWorkingNote.getTitleBgResId());
            floatingActionButton.setColorPressedResId(mWorkingNote.getBgColorResId());
            floatingActionButton.hide(false);
            floatingActionButton.setOnClickListener(this);
            FloatingActionButtonDecorator fabDecorator = new FloatingActionButtonDecorator(floatingActionButton);
            fabDecorator.setLabelText(stringId);
            mMenu.addMenuButton(floatingActionButton);

            final Label label = (Label) floatingActionButton.getTag(com.github.clans.fab.R.id.fab_label);
            label.setVisibility(View.INVISIBLE);
        }
        mMenu.open(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    public void onClick(View v) {
        int id = v.getId();
        if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }

        // floating action menu onclick
        if (v.getTag() == null) {
            return;
        }

        int stringId = (int) v.getTag();
        if (stringId != 0) {
            mMenu.close(true);
        }
        switch (stringId) {
            case R.string.notelist_menu_new:
                createNewNote();
                break;
            case R.string.menu_delete:
                deleteNote();
                break;
            case R.string.menu_font_size:
                showFontSeting();
                break;
            case R.string.menu_normal_mode:
                mWorkingNote.setCheckListMode(0);
                break;
            case R.string.menu_list_mode:
                mWorkingNote.setCheckListMode(TextNote.MODE_CHECK_LIST);
                break;
            case R.string.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            case R.string.menu_send_to_desktop:
                sendToDesktop();
                break;
            case R.string.menu_remove_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            case R.string.menu_alert:
                setReminder();
                break;
            case R.string.notelist_menu_color_theme:
                isShowTheme = true;
                break;
            case R.string.theme_blue:
            case R.string.theme_red:
            case R.string.theme_green:
            case R.string.theme_brown:
            case R.string.theme_white:
                mWorkingNote.setBgColorId(colorBtnMap.get(stringId));
                break;
            default:
                Log.i(TAG, "do noting");
                break;
        }
    }

    private void showThemeMenu() {
        ArrayList<Integer> colorIds = new ArrayList<>();
        colorIds.add(R.string.theme_blue);
        colorIds.add(R.string.theme_red);
        colorIds.add(R.string.theme_green);
        colorIds.add(R.string.theme_brown);
        colorIds.add(R.string.theme_white);
        addMenuItemAndOpenMenu(colorIds);
    }

    @Override
    public void onBackPressed() {
        onBackAction();
    }

    private void onBackAction() {
        if (clearSettingState()) {
            return;
        }
        saveNote();
        animateRevealHide(mAnimTargetView, mAnimNewNoteView);
        ActivityCompat.finishAfterTransition(this);
    }

    private boolean clearSettingState() {
        if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    public void onBackgroundColorChanged() {
        setNoteTheme();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackAction();
                break;
            case R.id.menu_new_note:
                createNewNote();
                break;
            case R.id.menu_delete:
                deleteNote();
                break;
            case R.id.menu_font_size:
                showFontSeting();
                break;
            case R.id.menu_list_mode:
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                        TextNote.MODE_CHECK_LIST : 0);
                break;
            case R.id.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            case R.id.menu_send_to_desktop:
                sendToDesktop();
                break;
            case R.id.menu_alert:
                setReminder();
                break;
            case R.id.menu_delete_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            default:
                break;
        }
        return true;
    }

    private void showFontSeting() {
        mFontSizeSelector.setVisibility(View.VISIBLE);
        findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
    }

    private void deleteNote() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.alert_title_delete));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(getString(R.string.alert_message_delete_note));
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteCurrentNote();
                        finish();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);
            }
        });
        d.show();
    }

    /**
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            mToolbar.setSubtitle(getSubTitle());
            if (!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    public void onWidgetChanged() {
        updateWidget();
    }

    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if (index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if (index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if (hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    private String getSubTitle() {
        String dateStr = DateUtils.formatDateTime(this, mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_TIME);
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            String alertStr = "";
            if (time > mWorkingNote.getAlertDate()) {
                alertStr = getString(R.string.note_alert_expired);
            } else {
                alertStr = (String) DateUtils.getRelativeTimeSpanString(mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS);
            }
            //中间空白符为emoji表情
            return dateStr + " | ⏰️️" + alertStr;
        }
        return dateStr;
    }

    private class FloatingActionButtonDecorator {
        private FloatingActionButton fab;

        public FloatingActionButtonDecorator(FloatingActionButton fab) {
            this.fab = fab;
        }

        public void setLabelText(int stringId) {
            fab.setLabelText(getString(stringId));
            fab.setTag(stringId);
            switch (stringId) {
                case R.string.notelist_menu_new:
                    fab.setImageResource(R.drawable.edit_note);
                    break;
                case R.string.menu_delete:
                    fab.setImageResource(R.drawable.delete);
                    break;
                case R.string.menu_font_size:
                    fab.setImageResource(R.drawable.font_size);
                    break;
                case R.string.menu_normal_mode:
                    fab.setImageResource(R.drawable.exit_todo_list);
                    break;
                case R.string.menu_list_mode:
                    fab.setImageResource(R.drawable.todo_list);
                    break;
                case R.string.menu_share:
                    fab.setImageResource(R.drawable.share_other);
                    break;
                case R.string.menu_send_to_desktop:
                    fab.setImageResource(R.drawable.send_desktop);
                    break;
                case R.string.menu_remove_remind:
                    fab.setImageResource(R.drawable.delete);
                    break;
                case R.string.menu_alert:
                    fab.setImageResource(R.drawable.clock_alert);
                    break;
                case R.string.notelist_menu_color_theme:
                    fab.setImageResource(R.drawable.theme_color);
                    break;
                case R.string.theme_blue:
                    fab.setColorNormalResId(R.color.primary_color_blue);
                    fab.setColorPressedResId(R.color.primary_color_blue_light);
                    break;
                case R.string.theme_red:
                    fab.setColorNormalResId(R.color.primary_color_red);
                    fab.setColorPressedResId(R.color.primary_color_red_light);
                    break;
                case R.string.theme_green:
                    fab.setColorNormalResId(R.color.primary_color_green);
                    fab.setColorPressedResId(R.color.primary_color_green_light);
                    break;
                case R.string.theme_brown:
                    fab.setColorNormalResId(R.color.primary_color_brown);
                    fab.setColorPressedResId(R.color.primary_color_brown_light);
                    break;
                case R.string.theme_white:
                    fab.setColorNormalResId(R.color.primary_color_white);
                    fab.setColorPressedResId(R.color.primary_color_white_light);
                    break;
                default:
                    break;
            }
        }
    }
}
